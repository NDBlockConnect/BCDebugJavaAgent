package dev.blockconnect.bcagent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.blockconnect.bcagent.core.AgentConfig;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Raw-socket control plane used when {@code com.sun.net.httpserver} is not
 * resolvable — notably when agent classes are loaded from the bootstrap class
 * path inside MC 1.21+ bundler servers, where the boot loader's unnamed module
 * cannot link against the {@code jdk.httpserver} module.
 * <p>
 * Implements a minimal HTTP/1.1 subset: one request per connection
 * ({@code Connection: close}), GET /status, GET /methods, GET /logs,
 * POST /export. Binds loopback only.
 */
public final class RawSocketControlServer implements ControlPlane {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final AgentConfig config;
    private final int port;
    private volatile ServerSocket serverSocket;
    private Thread acceptorThread;
    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    public RawSocketControlServer(int port, AgentConfig config) {
        this.port = port;
        this.config = config;
    }

    @Override
    public void start() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.bind(new InetSocketAddress("127.0.0.1", port), 16);
        acceptorThread = new Thread(this::acceptLoop, "BCDebug-ControlPlane");
        acceptorThread.setDaemon(true);
        acceptorThread.start();
    }

    @Override
    public void stop() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
        } catch (IOException ignored) {
        }
        pool.shutdownNow();
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                pool.execute(() -> handle(socket));
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    // Transient accept failure — brief backoff to avoid hot spin.
                    try { Thread.sleep(50); } catch (InterruptedException ie) { return; }
                }
            }
        }
    }

    private void handle(Socket socket) {
        try (Socket s = socket) {
            s.setSoTimeout(10_000);
            String requestLine = readLine(s.getInputStream());
            if (requestLine == null || requestLine.trim().isEmpty()) return;

            // Capture headers (token lives here when configured).
            java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
            String line;
            while ((line = readLine(s.getInputStream())) != null && !line.isEmpty()) {
                int colon = line.indexOf(':');
                if (colon > 0) {
                    headers.put(line.substring(0, colon).trim().toLowerCase(),
                        line.substring(colon + 1).trim());
                }
            }

            String[] parts = requestLine.trim().split("\\s+");
            String method = parts.length > 0 ? parts[0] : "";
            String path = parts.length > 1 ? parts[1] : "/";
            java.util.Map<String, String> query = new java.util.LinkedHashMap<>();
            int q = path.indexOf('?');
            if (q >= 0) {
                for (String pair : path.substring(q + 1).split("&")) {
                    int eq = pair.indexOf('=');
                    if (eq <= 0) continue;
                    query.put(pair.substring(0, eq), pair.substring(eq + 1));
                }
                path = path.substring(0, q);
            }

            // Token guard first on every endpoint (no-op when unset).
            String presented = headers.get(TokenGuard.HEADER.toLowerCase()) != null
                ? headers.get(TokenGuard.HEADER.toLowerCase())
                : query.get(TokenGuard.QUERY_PARAM);
            if (!TokenGuard.authorized(config, presented)) {
                respond(s, 401, "{\"error\":\"Unauthorized\"}");
                return;
            }

            if (path.equals("/status")) {
                respond(s, 200, GSON.toJson(ControlPayloads.status()));
            } else if (path.equals("/methods")) {
                respond(s, 200, GSON.toJson(ControlPayloads.methods(
                    query.getOrDefault("contains", ""),
                    parseInt(query.getOrDefault("min", "0")),
                    parseInt(query.getOrDefault("limit", "0")))));
            } else if (path.equals("/logs")) {
                respond(s, 200, GSON.toJson(ControlPayloads.logs(100)));
            } else if (path.equals("/classes")) {
                respond(s, 200, GSON.toJson(
                    AgentBootstrap.findLoadedClasses(
                        query.getOrDefault("contains", ""),
                        parseInt(query.getOrDefault("limit", "50")))));
            } else if (path.equals("/log-level")) {
                if (!"POST".equals(method)) {
                    respond(s, 405, "{\"error\":\"Method not allowed\"}");
                } else {
                    String level = query.get("level");
                    if (level == null || level.trim().isEmpty()) {
                        respond(s, 400, "{\"error\":\"Missing level\"}");
                    } else {
                        respond(s, 200, GSON.toJson(
                            ControlPayloads.logLevelChanged(
                                AgentBootstrap.setLogLevel(level))));
                    }
                }
            } else if (path.equals("/hooks/reload")) {
                if (!"POST".equals(method)) {
                    respond(s, 405, "{\"error\":\"Method not allowed\"}");
                } else {
                    respond(s, 200, GSON.toJson(AgentBootstrap.reloadHooks()));
                }
            } else if (path.equals("/export")) {
                if (!"POST".equals(method)) {
                    respond(s, 405, "{\"error\":\"Method not allowed\"}");
                } else {
                    try {
                        respond(s, 200, GSON.toJson(ControlPayloads.export(config)));
                    } catch (Exception e) {
                        respond(s, 500,
                            "{\"error\":" + GSON.toJson(String.valueOf(e.getMessage())) + "}");
                    }
                }
            } else {
                respond(s, 404, "{\"error\":\"Not found\"}");
            }
        } catch (Throwable ignored) {
            // Control plane must never take the game down over a bad request.
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(128);
        int prev = -1;
        int c;
        while ((c = in.read()) != -1) {
            if (prev == '\r' && c == '\n') break;
            prev = c;
            buf.write(c);
        }
        byte[] bytes = buf.toByteArray();
        // Strip trailing CR if the stream ended without LF.
        int len = bytes.length;
        if (len > 0 && bytes[len - 1] == '\r') len--;
        return new String(bytes, 0, len, StandardCharsets.US_ASCII);
    }

    private static void respond(Socket socket, int code, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        String head = "HTTP/1.1 " + code + " " + reason(code) + "\r\n"
            + "Content-Type: application/json; charset=utf-8\r\n"
            + "Content-Length: " + body.length + "\r\n"
            + "Connection: close\r\n\r\n";
        OutputStream out = socket.getOutputStream();
        out.write(head.getBytes(StandardCharsets.US_ASCII));
        out.write(body);
        out.flush();
    }

    private static String reason(int code) {
        switch (code) {
            case 200: return "OK";
            case 400: return "Bad Request";
            case 404: return "Not Found";
            case 405: return "Method Not Allowed";
            default: return "Internal Server Error";
        }
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return 50; }
    }
}
