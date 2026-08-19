package dev.blockconnect.bcagent.core;

import java.util.List;

public final class HookDispatcher {

    private static final AgentLogger LOG = AgentLogger.getInstance();

    public static void dispatchEntry(String className, String methodName, String descriptor,
                                     Object[] args) {
        HookRegistry registry = HookRegistry.getInstance();
        List<MethodHook> hooks = registry.getHooks(className);
        if (hooks == null) return;

        for (MethodHook hook : hooks) {
            if (!hook.matches(className, methodName, descriptor)) continue;
            if (hook.onEnter != null) {
                try {
                    hook.onEnter.invoke(className, methodName, descriptor, args, null, null);
                } catch (Throwable t) {
                    LOG.error("Hook onEnter error: " + hook + " — " + t.getMessage(), t);
                }
            }
        }
    }

    public static void dispatchExit(String className, String methodName, String descriptor,
                                    long startNanos, Object result, Throwable exception) {
        HookRegistry registry = HookRegistry.getInstance();
        List<MethodHook> hooks = registry.getHooks(className);
        if (hooks == null) return;

        for (MethodHook hook : hooks) {
            if (!hook.matches(className, methodName, descriptor)) continue;
            if (exception != null && hook.catchExceptions && hook.onExit != null) {
                try {
                    hook.onExit.invoke(className, methodName, descriptor, null, null, exception);
                } catch (Throwable t) {
                    LOG.error("Hook onExit error: " + hook + " — " + t.getMessage(), t);
                }
            } else if (hook.onExit != null) {
                try {
                    hook.onExit.invoke(className, methodName, descriptor, null, result, null);
                } catch (Throwable t) {
                    LOG.error("Hook onExit error: " + hook + " — " + t.getMessage(), t);
                }
            }
        }
    }
}
