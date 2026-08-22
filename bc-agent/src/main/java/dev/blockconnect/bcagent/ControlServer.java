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
        server.createContext("/export", exchange -> safe(exchange, () -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed");
                return;
            }
            sendJson(exchange, 200, ControlPayloads.export(config));
        }));

        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(2));
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
