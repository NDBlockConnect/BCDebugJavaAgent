package dev.blockconnect.bcagent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.blockconnect.bcagent.core.AgentConfig;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * JDK HttpServer-based control plane. Preferred frontend; when the
 * {@code jdk.httpserver} module is not linkable from agent code (boot-classpath
 * mode inside bundler servers), {@link RawSocketControlServer} takes over.
 */
public class ControlServer implements ControlPlane {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final HttpServer server;
    private final AgentConfig config;

    public ControlServer(int port, AgentConfig config) throws IOException {
        this.config = config;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);

        server.createContext("/status", exchange -> safe(exchange, () ->
            sendJson(exchange, 200, ControlPayloads.status())));
        server.createContext("/methods", exchange -> safe(exchange, () ->
            sendJson(exchange, 200, ControlPayloads.methods())));
        server.createContext("/logs", exchange -> safe(exchange, () ->
            sendJson(exchange, 200, ControlPayloads.logs(100))));
        server.createContext("/classes", exchange -> safe(exchange, () -> {
            java.util.Map<String, String> q = query(exchange.getRequestURI().getRawQuery());
            sendJson(exchange, 200, AgentBootstrap.findLoadedClasses(
                q.getOrDefault("contains", ""),
                parseInt(q.getOrDefault("limit", "50"), 50)));
        }));
        server.createContext("/log-level", exchange -> safe(exchange, () -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed");
                return;
            }
            java.util.Map<String, String> q = query(exchange.getRequestURI().getRawQuery());
            String level = q.get("level");
            if (level == null || level.isBlank()) {
                sendText(exchange, 400, "Missing ?level=");
                return;
            }
            sendJson(exchange, 200,
                ControlPayloads.logLevelChanged(AgentBootstrap.setLogLevel(level)));
        }));
        server.createContext("/hooks/reload", exchange -> safe(exchange, () -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed");
                return;
            }
            sendJson(exchange, 200, AgentBootstrap.reloadHooks());
        }));
        server.createContext("/export", exchange -> safe(exchange, () -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed");
                return;
            }
            sendJson(exchange, 200, ControlPayloads.export(config));
        }));

        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(2));
    }

    private static java.util.Map<String, String> query(String rawQuery) {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) return map;
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            map.put(urlDecode(pair.substring(0, eq)),
                urlDecode(pair.substring(eq + 1)));
        }
        return map;
    }

    private static String urlDecode(String s) {
        return java.net.URLDecoder.decode(s, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    @Override
    public void start() { server.start(); }

    @Override
    public void stop() { server.stop(0); }

    private interface EndpointAction {
        void run() throws IOException;
    }

    private static void safe(HttpExchange ex, EndpointAction action) {
        try {
            action.run();
        } catch (Throwable t) {
            try {
                sendText(ex, 500, "Internal error: " + t.getMessage());
            } catch (IOException ignored) {
            }
        }
    }

    private static void sendJson(HttpExchange ex, int code, Object obj) throws IOException {
        byte[] body = GSON.toJson(obj).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }

    private static void sendText(HttpExchange ex, int code, String text) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(body);
        }
    }
}
