package dev.blockconnect.bcagent.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RecordExporter {

    private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson GSON_LINE = new GsonBuilder().disableHtmlEscaping().create();

    public static List<String> exportAll(String outputDir) {
        List<String> written = new ArrayList<>();
        AgentLogger logger = AgentLogger.getInstance();

        try {
            Path dir = Paths.get(outputDir);
            Files.createDirectories(dir);

            written.add(exportLogs(dir, logger));
            written.add(exportMethodStats(dir));

            logger.info("Export complete: " + String.join(", ", written));
        } catch (IOException e) {
            logger.error("Export failed: " + e.getMessage(), e);
        }
        return written;
    }

    private static String exportLogs(Path dir, AgentLogger logger) throws IOException {
        Path logFile = dir.resolve("bcdebug-logs.jsonl");
        try (BufferedWriter w = Files.newBufferedWriter(logFile)) {
            for (AgentLogger.LogRecord rec : logger.snapshot()) {
                LogEntry entry = new LogEntry(
                    rec.timestamp != null ? rec.timestamp.toString() : null,
                    rec.level != null ? rec.level.name() : null,
                    rec.threadName,
                    rec.message,
                    rec.error != null ? rec.error.toString() : null
                );
                w.write(GSON_LINE.toJson(entry));
                w.newLine();
            }
        }
        return logFile.toString();
    }

    private static String exportMethodStats(Path dir) throws IOException {
        Path statsFile = dir.resolve("bcdebug-method-stats.json");
        Map<String, MethodRecorder.MethodStats> snapshot = MethodRecorder.snapshot();

        List<MethodStatEntry> entries = new ArrayList<>(snapshot.size());
        for (MethodRecorder.MethodStats s : snapshot.values()) {
            MethodStatEntry e = new MethodStatEntry();
            e.className = displayClassName(s.className);
            e.methodName = displayMethodName(s.className, s.methodName, s.descriptor);
            e.descriptor = s.descriptor;
            e.entryCount = s.entryCount.get();
            e.exitCount = s.exitCount.get();
            e.totalNanos = s.totalNanos.get();
            e.avgNanos = s.exitCount.get() > 0
                ? s.totalNanos.get() / s.exitCount.get() : 0;
            entries.add(e);
        }

        try (BufferedWriter w = Files.newBufferedWriter(statsFile)) {
            w.write(GSON_PRETTY.toJson(entries));
        }
        return statsFile.toString();
    }

    /** Display-friendly class name: reverse-translated when mappings active. */
    public static String displayClassName(String runtimeName) {
        RuntimeMappings mappings = HookRegistry.getInstance().getMappings();
        return mappings != null ? mappings.toDeobfName(runtimeName) : runtimeName;
    }

    /** Display-friendly method name: reverse-translated when mappings active.
     *  Lookup uses the raw runtime class name as mapping key. */
    public static String displayMethodName(String runtimeClass, String runtimeMethod,
                                            String descriptor) {
        RuntimeMappings mappings = HookRegistry.getInstance().getMappings();
        return mappings != null
            ? mappings.toDeobfMethodName(runtimeClass, runtimeMethod, descriptor)
            : runtimeMethod;
    }

    private static final class LogEntry {
        final String timestamp;
        final String level;
        final String thread;
        final String message;
        final String error;

        LogEntry(String ts, String lvl, String thread, String msg, String err) {
            this.timestamp = ts;
            this.level = lvl;
            this.thread = thread;
            this.message = msg;
            this.error = err;
        }
    }

    private static final class MethodStatEntry {
        String className;
        String methodName;
        String descriptor;
        long entryCount;
        long exitCount;
        long totalNanos;
        long avgNanos;
    }
}
