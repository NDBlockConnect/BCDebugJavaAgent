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

        String profile = config.hookProfile;
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
