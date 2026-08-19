package dev.blockconnect.bcagent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.blockconnect.bcagent.core.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ControlServer {

    private static final Gson GSON = new GsonBuilder().create();

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

    static class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                AgentLogger log = AgentLogger.getInstance();
                HookRegistry hooks = HookRegistry.getInstance();
                Map<String, Object> status = new LinkedHashMap<>();
                status.put("agent", "BCDebugJavaAgent");
                status.put("initialized", BCAgent.isInitialized());
                status.put("logRecords", log.getRecordCount());
                status.put("methodRecords", MethodRecorder.methodCount());
                status.put("hooksActive", hooks.isActive());
                status.put("totalHooks", hooks.totalHooks());
                sendJson(ex, 200, status);
            } catch (Throwable t) {
                sendText(ex, 500, "Internal error: " + t.getMessage());
            }
        }
    }

    static class MethodsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                Map<String, MethodRecorder.MethodStats> stats = MethodRecorder.snapshot();
                List<Map<String, Object>> list = new ArrayList<>(stats.size());
                for (MethodRecorder.MethodStats s : stats.values()) {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("class", s.className);
                    entry.put("method", s.methodName);
                    entry.put("entries", s.entryCount.get());
                    entry.put("exits", s.exitCount.get());
                    entry.put("totalNanos", s.totalNanos.get());
                    list.add(entry);
                }
                sendJson(ex, 200, list);
            } catch (Throwable t) {
                sendText(ex, 500, "Internal error: " + t.getMessage());
            }
        }
    }

    static class LogsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            try {
                AgentLogger.LogRecord[] records = AgentLogger.getInstance().snapshot();
                int limit = Math.min(records.length, 100);
                List<Map<String, Object>> list = new ArrayList<>(limit);
                for (int i = 0; i < limit; i++) {
                    AgentLogger.LogRecord r = records[i];
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("ts", r.timestamp != null ? r.timestamp.toString() : null);
                    entry.put("level", r.level != null ? r.level.name() : null);
                    entry.put("thread", r.threadName);
                    entry.put("msg", r.message);
                    list.add(entry);
                }
                sendJson(ex, 200, list);
            } catch (Throwable t) {
                sendText(ex, 500, "Internal error: " + t.getMessage());
            }
        }
    }

    class ExportHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            if (!"POST".equals(ex.getRequestMethod())) {
                sendText(ex, 405, "Method not allowed");
                return;
            }
            try {
                List<String> files = RecordExporter.exportAll(config.outputDir);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("exported", true);
                result.put("files", files);
                sendJson(ex, 200, result);
            } catch (Throwable t) {
                sendText(ex, 500, "Export failed: " + t.getMessage());
            }
        }
    }
}
