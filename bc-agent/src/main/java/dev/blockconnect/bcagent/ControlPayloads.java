package dev.blockconnect.bcagent;

import dev.blockconnect.bcagent.core.AgentConfig;
import dev.blockconnect.bcagent.core.AgentLogger;
import dev.blockconnect.bcagent.core.HookRegistry;
import dev.blockconnect.bcagent.core.MethodRecorder;
import dev.blockconnect.bcagent.core.RecordExporter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared endpoint payload builders for both control-plane frontends
 * (JDK HttpServer and raw ServerSocket). Keeps response shapes identical
 * regardless of which transport is active.
 */
final class ControlPayloads {

    private ControlPayloads() {}

    static Map<String, Object> status() {
        AgentLogger log = AgentLogger.getInstance();
        HookRegistry hooks = HookRegistry.getInstance();
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("agent", "BCDebugJavaAgent");
        status.put("version", AgentBootstrap.getVersion());
        status.put("initialized", AgentBootstrap.isInitialized());
        status.put("logRecords", log.getRecordCount());
        status.put("methodRecords", MethodRecorder.methodCount());
        status.put("hooksActive", hooks.isActive());
        status.put("totalHooks", hooks.totalHooks());
        status.put("mutedPrefixes", MethodRecorder.getMutedPrefixes().length);
        AgentConfig cfg = AgentBootstrap.getConfig();
        status.put("tokenEnabled", cfg != null && TokenGuard.isEnabled(cfg));
        return status;
    }

    static List<Map<String, Object>> methods() {
        return methods("", 0, 0);
    }

    /**
     * Filtered method statistics: {@code contains} matches the class name
     * substring, {@code min} filters by entry count, {@code limit} caps rows
     * (0 = no cap). Sorting by entry count descending when limiting applies.
     */
    static List<Map<String, Object>> methods(String contains, int min, int limit) {
        Map<String, MethodRecorder.MethodStats> stats = MethodRecorder.snapshot();
        String needle = contains == null ? "" : contains;

        List<Map<String, Object>> list = new ArrayList<>();
        for (MethodRecorder.MethodStats s : stats.values()) {
            if (!needle.isEmpty() && !s.className.contains(needle)) continue;
            if (min > 0 && s.entryCount.get() < min) continue;

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("class", RecordExporter.displayClassName(s.className));
            entry.put("method", RecordExporter.displayMethodName(
                s.className, s.methodName, s.descriptor));
            entry.put("descriptor", RecordExporter.displayDescriptor(s.descriptor));
            entry.put("entries", s.entryCount.get());
            entry.put("exits", s.exitCount.get());
            entry.put("totalNanos", s.totalNanos.get());
            list.add(entry);
        }

        if (limit > 0 && list.size() > limit) {
            list.sort((a, b) -> Long.compare(
                (Long) b.get("entries"), (Long) a.get("entries")));
            list = new ArrayList<>(list.subList(0, limit));
        }
        return list;
    }

    static List<Map<String, Object>> logs(int limit) {
        return logs(null, "", limit);
    }

    /**
     * Filtered log records: {@code minLevel} keeps records at or above the
     * given severity (TRACE..ERROR), {@code contains} matches the message
     * substring, {@code limit} caps rows. Newest last.
     */
    static List<Map<String, Object>> logs(String minLevel, String contains, int limit) {
        AgentLogger.LogRecord[] records = AgentLogger.getInstance().snapshot();
        String needle = contains == null ? "" : contains;

        AgentLogger.Level floor = null;
        if (minLevel != null && !minLevel.trim().isEmpty()) {
            try {
                floor = AgentLogger.Level.valueOf(minLevel.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                floor = null;
            }
        }

        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = records.length - 1; i >= 0; i--) {
            if (limit > 0 && list.size() >= limit) break;
            AgentLogger.LogRecord r = records[i];
            if (floor != null && (r.level == null || r.level.severity < floor.severity)) continue;
            if (!needle.isEmpty() && (r.message == null || !r.message.contains(needle))) continue;

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("ts", r.timestamp != null ? r.timestamp.toString() : null);
            entry.put("level", r.level != null ? r.level.name() : null);
            entry.put("thread", r.threadName);
            entry.put("msg", r.message);
            list.add(entry);
        }
        return list;
    }

    static Map<String, Object> export(AgentConfig config) throws IOException {
        List<String> files = RecordExporter.exportAll(config.outputDir);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("exported", true);
        result.put("files", files);
        return result;
    }

    static Map<String, Object> logLevelChanged(String newLevel) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("logLevel", newLevel);
        result.put("changed", true);
        return result;
    }
}
