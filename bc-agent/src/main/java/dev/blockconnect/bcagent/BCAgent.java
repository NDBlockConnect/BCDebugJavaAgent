package dev.blockconnect.bcagent;

import java.io.File;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarFile;

/**
 * Minimal premain/agentmain shim.
 * <p>
 * This class deliberately contains <b>no</b> compile-time references to any
 * other agent class. Launch environments resolve {@code Premain-Class} through
 * the application loader; if this shim referenced core classes directly, they
 * would be defined by the application loader before we can relocate them,
 * producing cross-loader linkage errors. Instead the shim:
 *
 * <ol>
 *   <li>locates its own agent JAR,</li>
 *   <li>extracts the embedded bootstrap JAR to a temp file,</li>
 *   <li>appends it to the bootstrap class loader search
 *       ({@link Instrumentation#appendToBootstrapClassLoaderSearch}), making
 *       every agent class visible to isolated game classloaders (MC 1.21+
 *       bundler servers) without an external {@code -Xbootclasspath/a},</li>
 *   <li>forces the bootstrap loader to define the entry class via a
 *       parent-less classloader, and hands off reflectively.</li>
 * </ol>
 */
public final class BCAgent {

    static final String BOOTSTRAP_RESOURCE = "META-INF/bootstrap/bcdebug-bootstrap.jar";
    static final String BOOTSTRAP_ENTRY_CLASS = "dev.blockconnect.bcagent.AgentBootstrap";

    private static volatile boolean bootstrapped = false;

    /** Strong reference: JarFile must stay open for the JVM lifetime. */
    private static volatile JarFile bootstrapJarHandle;

    private BCAgent() {}

    public static void premain(String args, Instrumentation inst) {
        run(args, inst);
    }

    public static void agentmain(String args, Instrumentation inst) {
        run(args, inst);
    }

    private static synchronized void run(String args, Instrumentation inst) {
        if (bootstrapped) return;
        try {
            File self = locateSelf();
            File bootstrapJar = extractNestedJar(self, BOOTSTRAP_RESOURCE);

            bootstrapJarHandle = new JarFile(bootstrapJar);
            // Bootstrap append: guarantees one consistent definition site for
            // agent classes even under isolated game classloaders.
            inst.appendToBootstrapClassLoaderSearch(bootstrapJarHandle);
            // System append: programmatic boot-append exposes CLASSES to the
            // bootstrap loader but NOT resources (its resource URLClassPath is
            // snapshotted at JVM start). Appending to the system search as well
            // lets ServiceLoader/getResources see META-INF/services while
            // parent-first delegation keeps the bootstrap definitions canonical.
            try {
                inst.appendToSystemClassLoaderSearch(bootstrapJarHandle);
            } catch (Throwable secondaryFailure) {
                // Non-fatal: only SPI-style discovery degrades.
            }

            // Parent-less loader delegates straight to the bootstrap loader,
            // guaranteeing the entry class (and everything it pulls in) is
            // defined exactly once, by the bootstrap loader.
            ClassLoader bootOnly = new ClassLoader(null) {};
            Class<?> entry = Class.forName(BOOTSTRAP_ENTRY_CLASS, true, bootOnly);
            entry.getMethod("bootstrap", String.class, Instrumentation.class)
                .invoke(null, args, inst);

            bootstrapped = true;
        } catch (Throwable t) {
            System.err.println("[BCDebugJavaAgent] Bootstrap failed: " + t);
        }
    }

    private static File locateSelf() throws Exception {
        return new File(BCAgent.class.getProtectionDomain()
            .getCodeSource().getLocation().toURI());
    }

    /**
     * Extracts a nested JAR entry to a temporary file. JarFile cannot read
     * nested archives in place, and appendToBootstrapClassLoaderSearch needs a
     * real file path.
     */
    private static File extractNestedJar(File self, String resource) throws Exception {
        try (JarFile outer = new JarFile(self)) {
            java.util.jar.JarEntry entry = outer.getJarEntry(resource);
            if (entry == null) {
                throw new IllegalStateException(
                    "Missing " + resource + " inside " + self.getName()
                    + " — not a BCDebugJavaAgent fat jar?");
            }
            Path target = Files.createTempFile("bcdebug-bootstrap-", ".jar");
            try (InputStream in = outer.getInputStream(entry)) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            target.toFile().deleteOnExit();
            return target.toFile();
        }
    }

    /** Compatibility accessor — real state lives in AgentBootstrap. */
    public static boolean isBootstrapped() { return bootstrapped; }
}
