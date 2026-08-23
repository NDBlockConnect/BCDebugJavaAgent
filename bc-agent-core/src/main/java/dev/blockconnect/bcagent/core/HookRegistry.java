package dev.blockconnect.bcagent.core;

import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Hook registry — central dispatcher for MC-version-specific hooks.
 * <p>
 * Hook modules (e.g. bc-hooks-26) implement {@link HookProvider} and are
 * discovered via Java SPI ({@code META-INF/services}). The registry matches
 * class+method patterns and dispatches to registered hook callbacks.
 */
public final class HookRegistry {

    private static final HookRegistry INSTANCE = new HookRegistry();

    /** Map of className -> list of hooks targeting that class. */
    private final Map<String, List<MethodHook>> hooksByClass = new ConcurrentHashMap<>();

    /** Active hook providers. */
    private final List<HookProvider> providers = new ArrayList<>();

    /** Whether hooks are active. */
    private volatile boolean active = false;

    /** Mappings currently in effect (set by translateWith), or null. */
    private volatile RuntimeMappings mappings;

    public static HookRegistry getInstance() {
        return INSTANCE;
    }

    private HookRegistry() {}

    /**
     * Initialize the registry: discover hook providers via SPI and register
     * their hooks for the given MC version.
     */
    public void init(AgentConfig config, Instrumentation inst) {
        if (!config.enableHooks) {
            AgentLogger.getInstance().info("Hooks disabled by config");
            return;
        }

        String profile = resolveProfile(config.hookProfile);
        AgentLogger.getInstance().info("Loading hook providers for profile: " + profile);

        // Discover via ServiceLoader (thread-context loader). Works in every
        // launch mode: plain -javaagent, self-bootstrapped shim, and
        // -Xbootclasspath/a — the system classpath always exposes the merged
        // service files while parent delegation keeps provider classes
        // defined by the bootstrap loader alongside the registry.
        for (HookProvider provider : ServiceLoader.load(HookProvider.class)) {
            if (provider.matchesProfile(profile)) {
                provider.register(inst);
                providers.add(provider);
                for (MethodHook hook : provider.getHooks()) {
                    registerHook(hook);
                }
                AgentLogger.getInstance().info(
                    "Loaded hook provider: " + provider.name()
                    + " (" + provider.getHooks().size() + " hooks)");
            }
        }

        if (providers.isEmpty()) {
            AgentLogger.getInstance().warn("No hook providers matched profile: " + profile);
        } else {
            active = true;
            AgentLogger.getInstance().info("Hook registry active: " + hooksByClass.size()
                + " classes, " + providers.size() + " providers");
        }

        if (active && config.mappingsFile != null && !config.mappingsFile.isBlank()) {
            applyMappingsFromFile(config.mappingsFile);
        } else if (active && config.mappingsAuto) {
            applyMappingsAuto(config);
        }
    }

    /**
     * Automatic mapping resolution for legacy (mapped) profiles: detect the
     * game version, reuse the cache or download from piston-meta, then apply.
     * Unobfuscated versions (26.x) skip this without any network access.
     */
    private void applyMappingsAuto(AgentConfig config) {
        try {
            String mcVersion = McVersionDetector.detect();
            String profile = McVersionDetector.toProfile(mcVersion);
            if ("1.21".equals(profile) || "1.20".equals(profile)) {
                AgentLogger.getInstance().info(
                    "mappingsAuto: resolving " + mcVersion + " mappings …");
                java.nio.file.Path cache = java.nio.file.Paths.get(
                    config.outputDir, "mappings-cache");
                RuntimeMappings resolved = MappingAutoDiscovery.acquire(mcVersion, cache);
                if (resolved != null) {
                    translateWith(resolved);
                    return;
                }
                AgentLogger.getInstance().warn(
                    "mappingsAuto: no mappings available — hooks stay unmapped");
            } else {
                AgentLogger.getInstance().info(
                    "mappingsAuto: profile '" + profile
                    + "' needs no runtime mappings (unobfuscated)");
            }
        } catch (Throwable t) {
            AgentLogger.getInstance().error(
                "mappingsAuto failed: " + t.getMessage(), t);
        }
    }

    private void applyMappingsFromFile(String mappingsPath) {
        try {
            translateWith(RuntimeMappings.load(java.nio.file.Paths.get(mappingsPath)));
        } catch (Throwable t) {
            AgentLogger.getInstance().error(
                "Failed to apply mappings file '" + mappingsPath + "': " + t.getMessage(), t);
        }
    }

    /** Shared hook-target translation + registry rebuild. */
    private void translateWith(RuntimeMappings resolved) {
        Map<String, List<MethodHook>> translated = new ConcurrentHashMap<>();
        int rewrittenClasses = 0;
        int rewrittenMethods = 0;
        for (List<MethodHook> hooks : hooksByClass.values()) {
            for (MethodHook hook : hooks) {
                String runtimeClass = resolved.toRuntimeName(hook.className);
                String runtimeMethod = resolved.toRuntimeMethodName(
                    hook.className, hook.methodName, hook.descriptor);

                if (runtimeClass.equals(hook.className)
                    && runtimeMethod.equals(hook.methodName)) {
                    translated.computeIfAbsent(hook.className,
                        k -> new ArrayList<>()).add(hook);
                    continue;
                }
                if (!runtimeClass.equals(hook.className)) rewrittenClasses++;
                if (!runtimeMethod.equals(hook.methodName)) rewrittenMethods++;
                MethodHook effective = new MethodHook(runtimeClass, runtimeMethod,
                    hook.descriptor, hook.onEnter, hook.onExit,
                    hook.catchExceptions, hook.description);
                translated.computeIfAbsent(effective.className,
                    k -> new ArrayList<>()).add(effective);
            }
        }
        hooksByClass.clear();
        hooksByClass.putAll(translated);
        mappings = resolved;
        AgentLogger.getInstance().info("Applied " + resolved.size()
            + " runtime mappings — " + rewrittenClasses
            + " class targets / " + rewrittenMethods + " method targets translated");
    }

    /** Mappings currently in effect, or null. Used by export translation. */
    public RuntimeMappings getMappings() { return mappings; }

    /**
     * Resolve the "auto" profile against the running MC version.
     * Explicit profiles pass through unchanged; "auto" attempts detection via
     * {@link McVersionDetector} and falls back to "auto" (default provider
     * behavior) when the version cannot be determined.
     */
    private static String resolveProfile(String profile) {
        if (!"auto".equalsIgnoreCase(profile)) return profile;

        String mcVersion = McVersionDetector.detect();
        String resolved = McVersionDetector.toProfile(mcVersion);
        if (resolved != null) {
            AgentLogger.getInstance().info(
                "Auto-detected MC version " + mcVersion + " — hook profile: " + resolved);
            return resolved;
        }
        AgentLogger.getInstance().warn(
            "Hook profile 'auto': MC version not detected, using default provider matching");
        return "auto";
    }

    /** Register a single method hook. */
    public void registerHook(MethodHook hook) {
        hooksByClass.computeIfAbsent(hook.className, k -> new ArrayList<>()).add(hook);
    }

    /** Get hooks for a given class (internal name format, e.g. "net/minecraft/..."). */
    public List<MethodHook> getHooks(String className) {
        return hooksByClass.get(className);
    }

    /** Check if any hooks target the given class. */
    public boolean hasHooks(String className) {
        return hooksByClass.containsKey(className);
    }

    public boolean isActive() { return active; }
    public List<HookProvider> getProviders() { return providers; }

    /** Total registered hooks. */
    public int totalHooks() {
        int total = 0;
        for (List<MethodHook> list : hooksByClass.values()) total += list.size();
        return total;
    }
}
