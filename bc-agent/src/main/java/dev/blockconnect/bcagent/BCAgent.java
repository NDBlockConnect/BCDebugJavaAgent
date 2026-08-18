package dev.blockconnect.bcagent;

import dev.blockconnect.bcagent.core.*;

import java.lang.instrument.Instrumentation;

/**
 * BCDebugJavaAgent — main agent entry point.
 * <p>
 * This class is referenced in the JAR manifest as both {@code Premain-Class}
 * (for -javaagent startup) and {@code Agent-Class} (for runtime attach).
 *
 * <h3>Usage</h3>
 * <pre>
 * java -javaagent:bcdebug-javaagent.jar=outputDir=bcdebug-output,logLevel=DEBUG \
 *      -jar minecraft.jar
 * </pre>
 *
 * <h3>Configuration keys</h3>
 * <ul>
 *   <li>{@code outputDir} — output directory for logs and exports</li>
 *   <li>{@code logLevel} — TRACE, DEBUG, INFO, WARN, ERROR</li>
 *   <li>{@code classFilters} — semicolon-separated class name prefixes to match</li>
 *   <li>{@code enableHooks} — enable MC-specific hook injection (true/false)</li>
 *   <li>{@code hookProfile} — which hook set to load: "26", "1.21", "auto"</li>
 *   <li>{@code exportOnShutdown} — export JSONL logs on JVM exit (true/false)</li>
 * </ul>
 */
public class BCAgent {

    private static final String AGENT_NAME = "BCDebugJavaAgent";
    private static final String AGENT_VERSION = "v26.0-Alpha.1";

    private static volatile boolean initialized = false;
    private static AgentConfig config;
    private static Instrumentation instrumentation;

    // ── Premain (startup via -javaagent) ─────────────────────

    /**
     * Called by the JVM when the agent is loaded at startup via -javaagent.
     */
    public static void premain(String args, Instrumentation inst) {
        initAgent(args, inst, false);
    }

    // ── Agentmain (runtime attach) ───────────────────────────

    /**
     * Called by the JVM when the agent is attached at runtime via Attach API.
     */
    public static void agentmain(String args, Instrumentation inst) {
        initAgent(args, inst, true);
    }

    // ── Common initialization ───────────────────────────────

    private static synchronized void initAgent(String args, Instrumentation inst,
                                                 boolean isAttach) {
        if (initialized) {
            AgentLogger.getInstance().warn(AGENT_NAME + " already initialized — skipping");
            return;
        }
        initialized = true;

        config = AgentConfig.parse(args);
        instrumentation = inst;

        // Initialize logger
        AgentLogger.init(config);

        AgentLogger log = AgentLogger.getInstance();
        log.info("========================================");
        log.info(AGENT_NAME + " " + AGENT_VERSION);
        log.info("========================================");
        log.info("Config: " + config);
        log.info("Attach mode: " + isAttach);
        log.info("Instrumentation: " + inst.getClass().getName());
        log.info("Can retransform: " + inst.isRetransformClassesSupported());
        log.info("Can redefine: " + inst.isRedefineClassesSupported());

        // Register the main bytecode analysis transformer
        BCTransformer bcTransformer = new BCTransformer(config);
        inst.addTransformer(bcTransformer, true);
        log.info("Registered BCTransformer (method logging)");

        // Register the hook injection transformer
        HookTransformer hookTransformer = new HookTransformer();
        inst.addTransformer(hookTransformer, true);
        log.info("Registered HookTransformer");

        // Initialize hook registry (discovers providers via SPI)
        HookRegistry.getInstance().init(config, inst);

        // Register shutdown hook for log export
        if (config.exportOnShutdown) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("JVM shutdown — exporting records...");
                RecordExporter.exportAll(config.outputDir);
                log.info("Export complete. Total method records: "
                    + MethodRecorder.methodCount());
            }, "BCDebug-Shutdown"));
            log.info("Registered shutdown hook for log export");
        }

        // Optionally start HTTP control server
        if (config.enableHttpServer) {
            try {
                ControlServer server = new ControlServer(config.httpPort, config);
                server.start();
                log.info("HTTP control server started on port " + config.httpPort);
            } catch (Throwable t) {
                log.error("Failed to start HTTP server: " + t.getMessage(), t);
            }
        }

        log.info(AGENT_NAME + " initialization complete");
        log.info("Method recorder active — instrumented classes matching: "
            + String.join(";", config.classFilters));
    }

    // ── Public API for runtime queries ──────────────────────

    public static AgentConfig getConfig() { return config; }
    public static Instrumentation getInstrumentation() { return instrumentation; }
    public static boolean isInitialized() { return initialized; }
}
