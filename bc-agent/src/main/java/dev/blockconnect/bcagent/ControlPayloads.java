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
        Map<String, MethodRecorder.MethodStats> stats = MethodRecorder.snapshot();
        List<Map<String, Object>> list = new ArrayList<>(stats.size());
        for (MethodRecorder.MethodStats s : stats.values()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("class", RecordExporter.displayClassName(s.className));
            entry.put("method", RecordExporter.displayMethodName(
                s.className, s.methodName, s.descriptor));
            entry.put("descriptor", s.descriptor);
            entry.put("entries", s.entryCount.get());
            entry.put("exits", s.exitCount.get());
            entry.put("totalNanos", s.totalNanos.get());
            list.add(entry);
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
