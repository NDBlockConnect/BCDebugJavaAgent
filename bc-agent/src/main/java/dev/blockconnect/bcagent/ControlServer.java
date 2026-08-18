package dev.blockconnect.bcagent;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.blockconnect.bcagent.core.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Minimal HTTP control server for runtime agent queries.
 * <p>
 * Endpoints:
 * <ul>
 *   <li>{@code GET /status} — agent status and record counts</li>
 *   <li>{@code GET /methods} — method statistics summary</li>
 *   <li>{@code GET /logs} — recent log entries (last 100)</li>
 *   <li>{@code POST /export} — trigger immediate export to disk</li>
 * </ul>
 */
public class ControlServer {

    private final HttpServer server;
    private final AgentConfig config;

    public ControlServer(int port, AgentConfig config) throws IOException {
        this.config = config;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);

        server.createContext("/status", new StatusHandler());
        server.createContext("/methods", new MethodsHandler());
        server.createContext("/logs", new LogsHandler());
        server.createContext("/export", new ExportHandler());

        server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(2));
    }

    public void start() { server.start(); }
    public void stop() { server.stop(0); }

    // ── Handlers ────────────────────────────────────────────

    private static void sendJson(HttpExchange ex, int code, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
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

    static class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            AgentLogger log = AgentLogger.getInstance();
            HookRegistry hooks = HookRegistry.getInstance();
            String json = "{\"agent\":\"BCDebugJavaAgent\","
                + "\"initialized\":" + BCAgent.isInitialized() + ","
                + "\"logRecords\":" + log.getRecordCount() + ","
                + "\"methodRecords\":" + MethodRecorder.methodCount() + ","
                + "\"hooksActive\":" + hooks.isActive() + ","
                + "\"totalHooks\":" + hooks.totalHooks() + "}";
            sendJson(ex, 200, json);
        }
    }

    static class MethodsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            Map<String, MethodRecorder.MethodStats> stats = MethodRecorder.snapshot();
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (MethodRecorder.MethodStats s : stats.values()) {
                if (!first) sb.append(',');
                first = false;
                sb.append("{\"class\":\"").append(s.className)
                  .append("\",\"method\":\"").append(s.methodName)
                  .append("\",\"entries\":").append(s.entryCount.get())
                  .append(",\"exits\":").append(s.exitCount.get())
                  .append(",\"totalNanos\":").append(s.totalNanos.get())
                  .append('}');
            }
            sb.append(']');
            sendJson(ex, 200, sb.toString());
        }
    }

    static class LogsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            AgentLogger.LogRecord[] records = AgentLogger.getInstance().snapshot();
            int limit = Math.min(records.length, 100);
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < limit; i++) {
                if (i > 0) sb.append(',');
                AgentLogger.LogRecord r = records[i];
                sb.append("{\"ts\":\"").append(r.timestamp)
                  .append("\",\"level\":\"").append(r.level)
                  .append("\",\"thread\":\"").append(r.threadName)
                  .append("\",\"msg\":\"").append(r.message.replace("\"", "\\\""))
                  .append("\"}");
            }
            sb.append(']');
            sendJson(ex, 200, sb.toString());
        }
    }

    class ExportHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equals(ex.getRequestMethod())) {
                sendText(ex, 405, "Method not allowed");
                return;
            }
            java.util.List<String> files = RecordExporter.exportAll(config.outputDir);
            StringBuilder sb = new StringBuilder("{\"exported\":true,\"files\":[");
            for (int i = 0; i < files.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append("\"").append(files.get(i).replace("\\", "/")).append("\"");
            }
            sb.append("]}");
            sendJson(ex, 200, sb.toString());
        }
    }
}
