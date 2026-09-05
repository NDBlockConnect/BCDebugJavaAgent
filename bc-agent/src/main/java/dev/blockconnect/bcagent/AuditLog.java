package dev.blockconnect.bcagent;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.blockconnect.bcagent.core.AgentConfig;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Structured audit trail for control-plane mutations, appended as one JSON
 * object per line to {@code <outputDir>/bcdebug-audit.log}. Best-effort:
 * audit failures never propagate to the caller or affect the game.
 */
public final class AuditLog {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Object LOCK = new Object();
    private static volatile String outputDir = "bcdebug-output";

    private AuditLog() {}

    public static void init(AgentConfig config) {
        outputDir = config.outputDir;
    }

    /** Append one audit record. */
    public static void record(String operation, String detail) {
        record(operation, detail, null);
    }

    /** Append one audit record with an optional caller address. */
    public static void record(String operation, String detail, String remote) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("ts", Instant.now().toString());
        entry.put("op", operation);
        if (remote != null && !remote.isEmpty()) entry.put("remote", remote);
        entry.put("detail", detail);
        String line = GSON.toJson(entry);
        try {
            Path dir = Paths.get(outputDir);
            Files.createDirectories(dir);
            Path file = dir.resolve("bcdebug-audit.log");
            synchronized (LOCK) {
                try (BufferedWriter w = Files.newBufferedWriter(file,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                    w.write(line);
                    w.newLine();
                }
            }
        } catch (IOException ignored) {
            // Best-effort audit — never break the operation being audited.
        }
    }
}
