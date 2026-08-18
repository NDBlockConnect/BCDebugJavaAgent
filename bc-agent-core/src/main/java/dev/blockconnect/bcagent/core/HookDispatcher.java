package dev.blockconnect.bcagent.core;

import java.util.List;

/**
 * Static dispatcher called from injected bytecode in instrumented methods.
 * <p>
 * This class is referenced by name in the injected bytecode, so its method
 * signatures must remain stable.
 */
public final class HookDispatcher {

    private static final AgentLogger LOG = AgentLogger.getInstance();

    /** Called at method entry by injected bytecode. */
    public static void dispatchEntry(String className, String methodName, String descriptor) {
        HookRegistry registry = HookRegistry.getInstance();
        List<MethodHook> hooks = registry.getHooks(className);
        if (hooks == null) return;

        for (MethodHook hook : hooks) {
            if (!hook.matches(className, methodName, descriptor)) continue;
            if (hook.onEnter != null) {
                try {
                    hook.onEnter.invoke(className, methodName, descriptor, null, null, null);
                } catch (Throwable t) {
                    LOG.error("Hook onEnter error: " + hook + " — " + t.getMessage(), t);
                }
            }
        }
    }

    /** Called at method exit by injected bytecode. */
    public static void dispatchExit(String className, String methodName, String descriptor,
                                    long startNanos) {
        HookRegistry registry = HookRegistry.getInstance();
        List<MethodHook> hooks = registry.getHooks(className);
        if (hooks == null) return;

        long duration = System.nanoTime() - startNanos;

        for (MethodHook hook : hooks) {
            if (!hook.matches(className, methodName, descriptor)) continue;
            if (hook.onExit != null) {
                try {
                    hook.onExit.invoke(className, methodName, descriptor, null, null, null);
                } catch (Throwable t) {
                    LOG.error("Hook onExit error: " + hook + " — " + t.getMessage(), t);
                }
            }
        }
    }
}
