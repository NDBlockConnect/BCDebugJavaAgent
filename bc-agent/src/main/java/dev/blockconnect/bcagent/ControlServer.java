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

        // Token guard runs first on every endpoint (no-op when unset).
        java.util.function.BiFunction<HttpExchange, java.util.Map<String, String>, Boolean> auth =
            (exchange, q) -> {
                boolean ok = TokenGuard.authorized(config,
                    exchange.getRequestHeaders().getFirst(TokenGuard.HEADER) != null
                        ? exchange.getRequestHeaders().getFirst(TokenGuard.HEADER)
                        : q.get(TokenGuard.QUERY_PARAM));
                if (!ok) {
                    AuditLog.record("auth-fail",
                        "path=" + exchange.getRequestURI().getPath(),
                        String.valueOf(exchange.getRemoteAddress()));
                }
                return ok;
            };

        // F-2 (v26.0 robustness assessment): read endpoints enforce GET,
        // write endpoints enforce POST — mirrors the raw-socket frontend.
        server.createContext("/status", exchange -> safe(exchange, () -> {
            java.util.Map<String, String> q = query(exchange.getRequestURI().getRawQuery());
            if (!auth.apply(exchange, q)) { send401(exchange); return; }
            if (!requireGet(exchange)) return;
            sendJson(exchange, 200, ControlPayloads.status());
        }));
        server.createContext("/methods", exchange -> safe(exchange, () -> {
            java.util.Map<String, String> q = query(exchange.getRequestURI().getRawQuery());
            if (!auth.apply(exchange, q)) { send401(exchange); return; }
            if (!requireGet(exchange)) return;
            sendJson(exchange, 200, ControlPayloads.methods(
                q.getOrDefault("contains", ""),
                parseInt(q.getOrDefault("min", "0"), 0),
                parseInt(q.getOrDefault("limit", "0"), 0)));
        }));
        server.createContext("/logs", exchange -> safe(exchange, () -> {
            java.util.Map<String, String> q = query(exchange.getRequestURI().getRawQuery());
            if (!auth.apply(exchange, q)) { send401(exchange); return; }
            if (!requireGet(exchange)) return;
            sendJson(exchange, 200, ControlPayloads.logs(
                q.get("level"),
                q.getOrDefault("contains", ""),
                parseInt(q.getOrDefault("limit", "100"), 100)));
        }));
        server.createContext("/classes", exchange -> safe(exchange, () -> {
            java.util.Map<String, String> q = query(exchange.getRequestURI().getRawQuery());
            if (!auth.apply(exchange, q)) { send401(exchange); return; }
            if (!requireGet(exchange)) return;
            sendJson(exchange, 200, AgentBootstrap.findLoadedClasses(
                q.getOrDefault("contains", ""),
                parseInt(q.getOrDefault("limit", "50"), 50)));
        }));
        server.createContext("/log-level", exchange -> safe(exchange, () -> {
            java.util.Map<String, String> q = query(exchange.getRequestURI().getRawQuery());
            if (!auth.apply(exchange, q)) { send401(exchange); return; }
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed");
                return;
            }
            String level = q.get("level");
            if (level == null || level.trim().isEmpty()) {
                sendText(exchange, 400, "Missing ?level=");
                return;
            }
            sendJson(exchange, 200,
                ControlPayloads.logLevelChanged(AgentBootstrap.setLogLevel(level)));
        }));
        server.createContext("/hooks/reload", exchange -> safe(exchange, () -> {
            java.util.Map<String, String> q = query(exchange.getRequestURI().getRawQuery());
            if (!auth.apply(exchange, q)) { send401(exchange); return; }
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed");
                return;
            }
            sendJson(exchange, 200, AgentBootstrap.reloadHooks());
        }));
        server.createContext("/filters", exchange -> safe(exchange, () -> {
            java.util.Map<String, String> q = query(exchange.getRequestURI().getRawQuery());
            if (!auth.apply(exchange, q)) { send401(exchange); return; }
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed");
                return;
            }
            String add = q.get("add");
            String remove = q.get("remove");
            sendJson(exchange, 200, AgentBootstrap.setFilters(
                add == null ? null : add.split(","),
                remove == null ? null : remove.split(",")));
        }));
        server.createContext("/export", exchange -> safe(exchange, () -> {
            java.util.Map<String, String> q = query(exchange.getRequestURI().getRawQuery());
            if (!auth.apply(exchange, q)) { send401(exchange); return; }
            if (!"POST".equals(exchange.getRequestMethod())) {
                sendText(exchange, 405, "Method not allowed");
                return;
            }
            sendJson(exchange, 200, ControlPayloads.export(config)); AuditLog.record("export", "manual", String.valueOf(exchange.getRemoteAddress()));
        }));

        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(2));
    }

    private static java.util.Map<String, String> query(String rawQuery) {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<>();
        if (rawQuery == null || rawQuery.trim().isEmpty()) return map;
        for (String pair : rawQuery.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            map.put(urlDecode(pair.substring(0, eq)),
                urlDecode(pair.substring(eq + 1)));
        }
        return map;
    }

    private static String urlDecode(String s) {
        try {
            return java.net.URLDecoder.decode(s, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return s; // UTF-8 is always supported; unreachable
        }
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

    /** F-2: send 405 for non-GET and signal the handler to stop via the
     *  returned flag (no exception-based control flow). */
    private static boolean requireGet(HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) {
            sendText(ex, 405, "Method not allowed");
            return false;
        }
        return true;
    }

    private static void send401(HttpExchange ex) throws IOException {
        sendText(ex, 401, "Unauthorized");
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
