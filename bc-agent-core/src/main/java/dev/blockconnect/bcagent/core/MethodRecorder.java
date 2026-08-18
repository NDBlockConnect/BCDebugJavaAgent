package dev.blockconnect.bcagent.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Records method-call statistics per class+method.
 * <p>
 * Updated from injected bytecode via static callbacks — must be lock-free and
 * allocation-free in the hot path.
 */
public final class MethodRecorder {

    private static final MethodRecorder INSTANCE = new MethodRecorder();

    // ── Per-method statistics ────────────────────────────────
    public static final class MethodStats {
        public final String className;
        public final String methodName;
        public final String descriptor;
        public final AtomicLong entryCount = new AtomicLong(0);
        public final AtomicLong exitCount = new AtomicLong(0);
        public final AtomicLong totalNanos = new AtomicLong(0);

        MethodStats(String cls, String name, String desc) {
            this.className = cls;
            this.methodName = name;
            this.descriptor = desc;
        }
    }

    private final ConcurrentHashMap<String, MethodStats> stats = new ConcurrentHashMap<>(4096);

    // ── Hot-path callbacks (called from injected bytecode) ───

    /** Called at method entry. Returns nano-time for duration measurement. */
    public static long onMethodEntry(String className, String methodName, String descriptor) {
        String key = key(className, methodName, descriptor);
        MethodStats s = INSTANCE.stats.computeIfAbsent(key,
            k -> new MethodStats(className, methodName, descriptor));
        s.entryCount.incrementAndGet();
        return System.nanoTime();
    }

    /** Called at method exit. */
    public static void onMethodExit(String className, String methodName, String descriptor, long startNanos) {
        String key = key(className, methodName, descriptor);
        MethodStats s = INSTANCE.stats.get(key);
        if (s != null) {
            s.exitCount.incrementAndGet();
            s.totalNanos.addAndGet(System.nanoTime() - startNanos);
        }
    }

    /** Called when an exception is thrown from a method. */
    public static void onMethodException(String className, String methodName, String descriptor, long startNanos) {
        String key = key(className, methodName, descriptor);
        MethodStats s = INSTANCE.stats.get(key);
        if (s != null) {
            s.totalNanos.addAndGet(System.nanoTime() - startNanos);
        }
    }

    // ── Query ────────────────────────────────────────────────

    public static Map<String, MethodStats> snapshot() {
        return new ConcurrentHashMap<>(INSTANCE.stats);
    }

    public static int methodCount() {
        return INSTANCE.stats.size();
    }

    private static String key(String cls, String name, String desc) {
        return cls + '#' + name + desc;
    }
}
