package dev.blockconnect.bcagent.core;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Centralized agent logger — thread-safe, lock-free ring buffer + file output.
 * <p>
 * Designed for minimal overhead in hot paths (method entry/exit logging).
 * Uses a pre-allocated lock-free ring buffer so logging never blocks game threads.
 */
public final class AgentLogger {

    public enum Level {
        TRACE(0), DEBUG(1), INFO(2), WARN(3), ERROR(4);

        public final int severity;
        Level(int s) { this.severity = s; }

        public static Level fromString(String s) {
            try { return Level.valueOf(s.toUpperCase()); }
            catch (Exception e) { return INFO; }
        }
    }

    // ── Singleton ────────────────────────────────────────────
    private static volatile AgentLogger instance;

    public static AgentLogger getInstance() {
        if (instance == null) {
            synchronized (AgentLogger.class) {
                if (instance == null) instance = new AgentLogger();
            }
        }
        return instance;
    }

    public static void init(AgentConfig config) {
        AgentLogger logger = getInstance();
        logger.level = Level.fromString(config.logLevel);
        logger.outputDir = config.outputDir;
    }

    // ── Fields ───────────────────────────────────────────────
    private Level level = Level.INFO;
    private String outputDir = "bcdebug-output";

    /** Ring buffer capacity for in-memory records. */
    private static final int RING_CAPACITY = 1 << 16; // 65536

    private final LogRecord[] ring = new LogRecord[RING_CAPACITY];
    private final AtomicInteger writeIdx = new AtomicInteger(0);
    private final AtomicInteger recordCount = new AtomicInteger(0);

    private AgentLogger() {
        for (int i = 0; i < RING_CAPACITY; i++) {
            ring[i] = new LogRecord();
        }
    }

    // ── Logging API ──────────────────────────────────────────

    public void trace(String msg) { log(Level.TRACE, msg, null); }
    public void debug(String msg) { log(Level.DEBUG, msg, null); }
    public void info(String msg)  { log(Level.INFO, msg, null); }
    public void warn(String msg)  { log(Level.WARN, msg, null); }
    public void error(String msg) { log(Level.ERROR, msg, null); }
    public void error(String msg, Throwable t) { log(Level.ERROR, msg, t); }

    public void log(Level lvl, String msg, Throwable t) {
        if (lvl.severity < level.severity) return;

        int idx = writeIdx.getAndIncrement() & (RING_CAPACITY - 1);
        LogRecord rec = ring[idx];
        rec.timestamp = Instant.now();
        rec.level = lvl;
        rec.threadName = Thread.currentThread().getName();
        rec.message = msg;
        rec.error = t;

        recordCount.incrementAndGet();

        // Also print to stderr for visibility during development
        if (lvl.severity >= Level.INFO.severity) {
            String line = formatRecord(rec);
            System.err.println(line);
        }
    }

    // ── Export ───────────────────────────────────────────────

    /** Get all records in the ring buffer (snapshot). */
    public LogRecord[] snapshot() {
        int count = Math.min(recordCount.get(), RING_CAPACITY);
        LogRecord[] result = new LogRecord[count];
        int start = (writeIdx.get() - count) & (RING_CAPACITY - 1);
        for (int i = 0; i < count; i++) {
            result[i] = ring[(start + i) & (RING_CAPACITY - 1)];
        }
        return result;
    }

    public int getRecordCount() { return recordCount.get(); }
    public String getOutputDir() { return outputDir; }

    // ── Formatting ───────────────────────────────────────────

    static String formatRecord(LogRecord rec) {
        StringBuilder sb = new StringBuilder(128);
        sb.append(DateTimeFormatter.ISO_INSTANT.format(rec.timestamp));
        sb.append(" [").append(rec.level).append("] [").append(rec.threadName).append("] ");
        sb.append(rec.message);
        if (rec.error != null) {
            sb.append('\n');
            StringWriter sw = new StringWriter();
            rec.error.printStackTrace(new PrintWriter(sw));
            sb.append(sw);
        }
        return sb.toString();
    }

    /** Mutable log record entry — reused in the ring buffer. */
    public static final class LogRecord {
        public Instant timestamp;
        public Level level;
        public String threadName;
        public String message;
        public Throwable error;
    }
}
