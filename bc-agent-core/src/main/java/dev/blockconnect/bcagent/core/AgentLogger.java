package dev.blockconnect.bcagent.core;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

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

    private static volatile AgentLogger instance;

    public static AgentLogger getInstance() {
        if (instance == null) {
            synchronized (AgentLogger.class) {
                if (instance == null) instance = new AgentLogger();
            }
        }
        return instance;
    }

    private Level level = Level.INFO;
    private String outputDir = "bcdebug-output";
    private boolean fileMirror = false;

    /** Live mirror writer; guarded by itself. Created lazily on first write. */
    private volatile java.io.BufferedWriter liveMirror;

    public static void init(AgentConfig config) {
        AgentLogger logger = getInstance();
        logger.level = Level.fromString(config.logLevel);
        logger.outputDir = config.outputDir;
        logger.fileMirror = config.logFile;
    }

    /** Change the effective level at runtime (control-plane operation). */
    public static void setLiveLevel(String levelName) {
        instance.level = Level.fromString(levelName);
    }

    public static String getLevelName() {
        return instance.level.name();
    }

    /** Close the live mirror (called from the shutdown hook before export). */
    public static void closeLiveMirror() {        AgentLogger logger = instance;
        java.io.BufferedWriter w = logger.liveMirror;
        if (w != null) {
            logger.liveMirror = null;
            try {
                synchronized (w) { w.close(); }
            } catch (Exception ignored) {
            }
        }
    }

    private void mirrorToFile(String line) {
        if (!fileMirror || line == null) return;
        try {
            java.io.BufferedWriter w = liveMirror;
            if (w == null) {
                synchronized (this) {
                    if (liveMirror == null) {
                        java.nio.file.Path dir = java.nio.file.Paths.get(outputDir);
                        Files.createDirectories(dir);
                        liveMirror = Files.newBufferedWriter(
                            dir.resolve("bcdebug-live.log"),
                            java.nio.charset.StandardCharsets.UTF_8);
                    }
                    w = liveMirror;
                }
            }
            if (w == null) return;
            synchronized (w) {
                if (liveMirror == null) return; // closed concurrently
                w.write(line);
                w.newLine();
                w.flush();
            }
        } catch (Throwable ignored) {
            // Mirroring must never break logging or the game.
        }
    }

    private static final int RING_CAPACITY = 1 << 16;

    private final LogRecord[] ring = new LogRecord[RING_CAPACITY];
    private final AtomicInteger writeIdx = new AtomicInteger(0);
    private final AtomicInteger recordCount = new AtomicInteger(0);

    private AgentLogger() {
        for (int i = 0; i < RING_CAPACITY; i++) {
            ring[i] = new LogRecord();
        }
    }

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
        synchronized (rec) {
            rec.timestamp = Instant.now();
            rec.level = lvl;
            rec.threadName = Thread.currentThread().getName();
            rec.message = msg;
            rec.error = t;
        }

        recordCount.incrementAndGet();

        String line = formatRecord(rec);
        mirrorToFile(line);
        if (lvl.severity >= Level.INFO.severity) {
            System.err.println(line);
        }
    }

    public LogRecord[] snapshot() {
        int count = Math.min(recordCount.get(), RING_CAPACITY);
        LogRecord[] result = new LogRecord[count];
        int start = (writeIdx.get() - count) & (RING_CAPACITY - 1);
        for (int i = 0; i < count; i++) {
            LogRecord src = ring[(start + i) & (RING_CAPACITY - 1)];
            LogRecord copy = new LogRecord();
            synchronized (src) {
                copy.timestamp = src.timestamp;
                copy.level = src.level;
                copy.threadName = src.threadName;
                copy.message = src.message;
                copy.error = src.error;
            }
            result[i] = copy;
        }
        return result;
    }

    public int getRecordCount() { return recordCount.get(); }
    public String getOutputDir() { return outputDir; }

    static String formatRecord(LogRecord rec) {
        StringBuilder sb = new StringBuilder(128);
        if (rec.timestamp != null) {
            sb.append(DateTimeFormatter.ISO_INSTANT.format(rec.timestamp));
        } else {
            sb.append("N/A");
        }
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

    public static final class LogRecord {
        public Instant timestamp;
        public Level level;
        public String threadName;
        public String message;
        public Throwable error;
    }
}
