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

/**
 * Exports collected records to disk in JSONL format.
 * <p>
 * Called on JVM shutdown (via shutdown hook) or on-demand via the HTTP API.
 */
public final class RecordExporter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Export all collected data to the output directory.
     * @param outputDir target directory (created if absent)
     * @return list of files written
     */
    public static List<String> exportAll(String outputDir) {
        List<String> written = new ArrayList<>();
        AgentLogger logger = AgentLogger.getInstance();

        try {
            Path dir = Paths.get(outputDir);
            Files.createDirectories(dir);

            // 1. Export log records as JSONL
            written.add(exportLogs(dir, logger));

            // 2. Export method statistics as JSON
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
                    rec.timestamp.toString(),
                    rec.level.name(),
                    rec.threadName,
                    rec.message,
                    rec.error != null ? rec.error.toString() : null
                );
                w.write(GSON.toJson(entry));
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
            e.className = s.className;
            e.methodName = s.methodName;
            e.descriptor = s.descriptor;
            e.entryCount = s.entryCount.get();
            e.exitCount = s.exitCount.get();
            e.totalNanos = s.totalNanos.get();
            e.avgNanos = s.exitCount.get() > 0
                ? s.totalNanos.get() / s.exitCount.get() : 0;
            entries.add(e);
        }

        try (BufferedWriter w = Files.newBufferedWriter(statsFile)) {
            w.write(GSON.toJson(entries));
        }
        return statsFile.toString();
    }

    // ── JSON DTOs ───────────────────────────────────────────

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
