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

        // Discover via ServiceLoader
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
    }

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
