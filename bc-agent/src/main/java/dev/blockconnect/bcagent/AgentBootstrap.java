package dev.blockconnect.bcagent;

import dev.blockconnect.bcagent.core.*;

import java.lang.instrument.Instrumentation;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Real agent entry point. Loaded by the bootstrap class loader through
 * {@link BCAgent}'s reflective handoff, so every core class referenced here
 * resolves exactly once from the bootstrap search path — regardless of how
 * the game isolates its own classloaders (e.g. MC 1.21+ bundler servers).
 *
 * <p>Do NOT add compile-time references to this class from {@code BCAgent}:
 * the shim must stay free of core-class references or the application loader
 * would define them first and poison cross-loader linkage.
 */
public final class AgentBootstrap {

    private static final String AGENT_NAME = "BCDebugJavaAgent";
    private static final String FALLBACK_VERSION = "v26.0-Alpha.5";
    private static String agentVersion;

    private static volatile boolean initialized = false;
    private static AgentConfig config;
    private static Instrumentation instrumentation;

    private AgentBootstrap() {}

    public static synchronized void bootstrap(String args, Instrumentation inst) {
        if (initialized) {
            AgentLogger.getInstance().warn(AGENT_NAME + " already initialized — skipping");
            return;
        }
        initialized = true;

        config = AgentConfig.parse(args);
        instrumentation = inst;

        AgentLogger.init(config);
        agentVersion = detectVersion();

        AgentLogger log = AgentLogger.getInstance();
        log.info("========================================");
        log.info(AGENT_NAME + " " + agentVersion);
        log.info("========================================");

        if (config.verbose) {
            log.info("Config: " + config);
            log.info("Instrumentation: " + inst.getClass().getName());
            log.info("Can retransform: " + inst.isRetransformClassesSupported());
            log.info("Can redefine: " + inst.isRedefineClassesSupported());
            log.info("Java version: " + System.getProperty("java.version"));
            log.info("Java vendor: " + System.getProperty("java.vendor"));
            log.info("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
            log.info("Bootstrap mode: self-appended (no -Xbootclasspath/a required)");
        }

        BCTransformer bcTransformer = new BCTransformer(config);
        inst.addTransformer(bcTransformer, true);
        log.info("Registered BCTransformer (method logging)");

        HookTransformer hookTransformer = new HookTransformer();
        inst.addTransformer(hookTransformer, true);
        log.info("Registered HookTransformer");

        HookRegistry.getInstance().init(config, inst);

        if (config.exportOnShutdown) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                log.info("JVM shutdown — exporting records...");
                AgentLogger.closeLiveMirror();
                RecordExporter.exportAll(config.outputDir);
                log.info("Export complete. Total method records: "
                    + MethodRecorder.methodCount());
            }, "BCDebug-Shutdown"));
            log.info("Registered shutdown hook for log export");
        }

        if (config.enableHttpServer) {
            try {
                ControlPlane plane;
                try {
                    // Preferred frontend. Constructing ControlServer may throw
                    // NoClassDefFoundError when jdk.httpserver is not linkable
                    // from bootstrap-loader code (MC 1.21+ bundler servers
                    // running with a self-appended/boot-classpath jar).
                    plane = new ControlServer(config.httpPort, config);
                } catch (LinkageError unavailable) {
                    log.warn("jdk.httpserver not linkable — falling back to raw socket control plane");
                    plane = new RawSocketControlServer(config.httpPort, config);
                }
                plane.start();
                log.info("HTTP control server started on port " + config.httpPort);
            } catch (Throwable t) {
                log.error("Failed to start HTTP server: " + t.getMessage(), t);
            }
        }

        log.info(AGENT_NAME + " initialization complete");
        log.info("Method recorder active — instrumented classes matching: "
            + String.join(";", config.classFilters));
    }

    private static String detectVersion() {
        try {
            java.net.URL url = AgentBootstrap.class.getResource("/META-INF/MANIFEST.MF");
            if (url != null) {
                Manifest manifest = new Manifest(url.openStream());
                Attributes attr = manifest.getMainAttributes();
                String version = attr.getValue("Implementation-Version");
                if (version != null && !version.isEmpty()) {
                    return version;
                }
            }
        } catch (Exception ignored) {
        }
        return FALLBACK_VERSION;
    }

    public static AgentConfig getConfig() { return config; }
    public static Instrumentation getInstrumentation() { return instrumentation; }
    public static boolean isInitialized() { return initialized; }
    public static String getVersion() { return agentVersion != null ? agentVersion : FALLBACK_VERSION; }
}
