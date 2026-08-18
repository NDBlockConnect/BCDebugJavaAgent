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

    /** Log level: TRACE, DEBUG, INFO, WARN, ERROR. */
    public String logLevel = "INFO";

    /** Whether to enable the HTTP control server (for runtime queries). */
    public boolean enableHttpServer = false;

    /** HTTP server port (if enabled). */
    public int httpPort = 25595;

    /** Hook profile: which set of MC-specific hooks to activate.
     *  Values: "26", "1.21", "1.20", "1.12", "auto". */
    public String hookProfile = "auto";

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
            case "loglevel"        -> logLevel = val.toUpperCase();
            case "enablehttp"      -> enableHttpServer = Boolean.parseBoolean(val);
            case "httpport"        -> httpPort = parseInt(val, httpPort);
            case "hookprofile"     -> hookProfile = val;
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
