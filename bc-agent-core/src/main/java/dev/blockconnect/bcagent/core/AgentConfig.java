package dev.blockconnect.bcagent.core;

/**
 * Agent configuration — parsed from -javaagent args or bcdebug.properties.
 * <p>
 * All fields have safe defaults so the agent can run with zero configuration.
 */
public class AgentConfig {

    /** Output directory for logs and exported data. */
    public String outputDir = "bcdebug-output";

    /** Comma-separated class name prefixes to match for instrumentation.
     *  Default: net.minecraft.,com.mojang. */
    public String[] classFilters = {"net.minecraft.", "com.mojang."};

    /** Comma-separated class name prefixes to exclude. */
    public String[] excludeFilters = {"net.minecraft.client.main.Main"};

    /**
     * Runtime-populated extra filters (hook target class names after mapping
     * translation). Managed by {@link HookRegistry}; user excludeFilters keep
     * priority over these.
     */
    public volatile String[] dynamicFilters = new String[0];

    /** Maximum method entry log records per class (0 = unlimited). */
    public int maxRecordsPerClass = 5000;

    /** Whether to log method entries. */
    public boolean logMethodEntry = true;

    /** Whether to log method exits. */
    public boolean logMethodExit = false;

    /** Whether to log field accesses (getfield/putfield). */
    public boolean logFieldAccess = false;

    /** Whether to record class load events. */
    public boolean logClassLoad = true;

    /** Whether to enable hook injection. */
    public boolean enableHooks = true;

    /** Whether to export logs as JSONL on JVM shutdown. */
    public boolean exportOnShutdown = true;

    /**
     * Periodic safety export interval in seconds (0 = disabled). Writes the
     * same artifacts as shutdown export on a fixed schedule so data survives
     * hard kills; each run overwrites {@code bcdebug-logs.jsonl} and
     * {@code bcdebug-method-stats.json}.
     */
    public int exportIntervalSec = 0;

    /** Log level: TRACE, DEBUG, INFO, WARN, ERROR. */
    public String logLevel = "INFO";

    /**
     * Mirror INFO+ log records into {@code <outputDir>/bcdebug-live.log}.
     * Independent of stdout/stderr, so headless launches with dead console
     * pipes still produce inspectable agent state.
     */
    public boolean logFile = false;

    /** Whether to enable the HTTP control server (for runtime queries). */
    public boolean enableHttpServer = false;

    /** HTTP server port (if enabled). */
    public int httpPort = 25595;

    /** Hook profile: which set of MC-specific hooks to activate.
     *  Values: "26", "1.21", "1.20", "1.12", "auto". */
    public String hookProfile = "auto";

    /**
     * Path to a ProGuard mapping file (Mojang {@code client.txt}/{@code server.txt}).
     * When set, hook targets authored against Mojang names are translated to
     * the runtime names used by obfuscated legacy jars (1.20.x / 1.21.x).
     */
    public String mappingsFile = "";

    /**
     * When true and {@link #mappingsFile} is empty, resolve mappings
     * automatically for detected legacy versions: reuse a cached file or
     * download from Mojang's piston-meta. Ignored on unobfuscated versions.
     */
    public boolean mappingsAuto = false;

    /** Verbose agent startup logging. */
    public boolean verbose = false;

    // ── Parsing ──────────────────────────────────────────────

    /**
     * Parse agent arguments from the -javaagent string.
     * Format: key1=value1,key2=value2 (same convention as standard agents).
     */
    public static AgentConfig parse(String args) {
        AgentConfig config = new AgentConfig();
        if (args == null || args.isBlank()) return config;

        for (String pair : args.split(",")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String key = pair.substring(0, eq).trim();
            String val = pair.substring(eq + 1).trim();
            config.set(key, val);
        }
        return config;
    }

    /** Set a config field by key name (case-insensitive). */
    public void set(String key, String val) {
        switch (key.toLowerCase()) {
            case "outputdir"       -> outputDir = val;
            case "classfilters"    -> classFilters = val.split(";");
            case "excludefilters"  -> excludeFilters = val.split(";");
            case "maxrecords"      -> maxRecordsPerClass = parseInt(val, maxRecordsPerClass);
            case "logmethodentry"   -> logMethodEntry = Boolean.parseBoolean(val);
            case "logmethodexit"    -> logMethodExit = Boolean.parseBoolean(val);
            case "logfieldaccess"  -> logFieldAccess = Boolean.parseBoolean(val);
            case "logclassload"    -> logClassLoad = Boolean.parseBoolean(val);
            case "enablehooks"     -> enableHooks = Boolean.parseBoolean(val);
            case "exportonshutdown" -> exportOnShutdown = Boolean.parseBoolean(val);
            case "exportintervalsec" -> exportIntervalSec = parseInt(val, exportIntervalSec);
            case "loglevel"        -> logLevel = val.toUpperCase();
            case "logfile"         -> logFile = Boolean.parseBoolean(val);
            case "enablehttp"      -> enableHttpServer = Boolean.parseBoolean(val);
            case "httpport"        -> httpPort = parseInt(val, httpPort);
            case "hookprofile"     -> hookProfile = val;
            case "mappingsfile"    -> mappingsFile = val;
            case "mappingsauto"    -> mappingsAuto = Boolean.parseBoolean(val);
            case "verbose"         -> verbose = Boolean.parseBoolean(val);
            default                -> { /* unknown key — ignore */ }
        }
    }

    /** Check if a class name matches the include filters. */
    public boolean matchesClass(String className) {
        // Always exclude
        for (String ex : excludeFilters) {
            if (className.startsWith(ex)) return false;
        }
        for (String inc : classFilters) {
            if (className.startsWith(inc)) return true;
        }
        for (String dyn : dynamicFilters) {
            if (className.startsWith(dyn)) return true;
        }
        return false;
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); }
        catch (NumberFormatException e) { return def; }
    }

    @Override
    public String toString() {
        return "AgentConfig{outputDir=" + outputDir
            + ", filters=" + String.join(";", classFilters)
            + ", hooks=" + enableHooks + ", profile=" + hookProfile
            + ", logLevel=" + logLevel + "}";
    }
}
