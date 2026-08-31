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
        AgentLogger.LogRecord[] records = AgentLogger.getInstance().snapshot();
        int capped = Math.min(records.length, limit);
        List<Map<String, Object>> list = new ArrayList<>(capped);
        for (int i = 0; i < capped; i++) {
            AgentLogger.LogRecord r = records[i];
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
