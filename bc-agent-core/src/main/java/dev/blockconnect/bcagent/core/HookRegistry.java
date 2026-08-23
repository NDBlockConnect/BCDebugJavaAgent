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

    /** Pristine provider snapshot (Mojang names) — source of truth for rebuilds. */
    private final List<MethodHook> originalHooks = new ArrayList<>();

    /** Guards against double-injection across load/retransform passes. */
    private final HookInjectionTracker injectionTracker = new HookInjectionTracker();

    /** Whether hooks are active. */
    private volatile boolean active = false;

    /** Mappings currently in effect (set by translateWith), or null. */
    private volatile RuntimeMappings mappings;

    /** Config captured at init — needed by reload to push dynamic filters. */
    private volatile AgentConfig boundConfig;

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
        this.boundConfig = config;

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
                    originalHooks.add(hook);
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

        if (active && !config.mappingsFile.isBlank()) {
            applyMappingsFromFile(config.mappingsFile);
        } else if (active && config.mappingsAuto) {
            applyMappingsAuto(config);
        }
    }

    /**
     * Re-resolve mappings (explicit file or auto discovery) and re-translate
     * hook targets from the pristine provider snapshot, then retransform
     * already-loaded target classes so hooks missed during boot still inject.
     *
     * @return summary counters for the control-plane response
     */
    public synchronized Map<String, Object> reload(Instrumentation inst,
                                                    AgentConfig config) {
        Map<String, Object> summary = new java.util.LinkedHashMap<>();
        int before = totalHooks();
        if (!config.mappingsFile.isBlank()) {
            applyMappingsFromFile(config.mappingsFile);
        } else {
            // An explicit reload IS the user intent: resolve mappings even
            // when mappingsAuto was left off at boot.
            applyMappingsAuto(config);
        }
        summary.put("hooks", totalHooks());
        summary.put("hookClasses", hooksByClass.size());

        int retransformed = 0;
        int failed = 0;
        Class<?>[] loaded = inst.getAllLoadedClasses();
        for (String target : hooksByClass.keySet()) {
            String dotted = target.replace('/', '.');
            for (Class<?> c : loaded) {
                if (c.getName().equals(dotted)) {
                    try {
                        inst.retransformClasses(c);
                        retransformed++;
                    } catch (Throwable t) {
                        failed++;
                        AgentLogger.getInstance().error(
                            "Retransform failed for " + dotted + ": " + t.getMessage(), t);
                    }
                    break;
                }
            }
        }
        summary.put("retransformed", retransformed);
        summary.put("failed", failed);
        AgentLogger.getInstance().info("Hooks reload: " + before + "->" + totalHooks()
            + " hooks, retransformed " + retransformed + " classes (" + failed + " failed)");
        return summary;
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

    /** Shared hook-target translation + registry rebuild.
     *  Always rebuilds from the pristine snapshot so repeated reloads are
     *  idempotent (no double translation). */
    private void translateWith(RuntimeMappings resolved) {
        Map<String, List<MethodHook>> translated = new ConcurrentHashMap<>();
        int rewrittenClasses = 0;
        int rewrittenMethods = 0;
        for (MethodHook hook : originalHooks) {
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
        hooksByClass.clear();
        hooksByClass.putAll(translated);
        mappings = resolved;

        // Auto-extend statistics coverage: every live hook target class name
        // becomes a dynamic filter so translated (possibly obfuscated)
        // targets always appear in method stats without manual classfilters.
        if (boundConfig != null) {
            boundConfig.dynamicFilters = translated.keySet().toArray(new String[0]);
        }

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

    /** Injection dedup tracker shared with the hook transformer. */
    public HookInjectionTracker getInjectionTracker() { return injectionTracker; }

    /** Live target class names (internal format) — used by retransform reload. */
    public java.util.Set<String> getTargetClassNames() { return hooksByClass.keySet(); }

    /** Total registered hooks. */
    public int totalHooks() {
        int total = 0;
        for (List<MethodHook> list : hooksByClass.values()) total += list.size();
        return total;
    }
}
