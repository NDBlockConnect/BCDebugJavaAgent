package dev.blockconnect.bcagent.core;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which class#method|descriptor pairs have already had hook dispatch
 * bytecode injected. Guards the retransform path: when a previously loaded
 * class is retransformed to pick up newly registered hooks, methods that were
 * already injected must be left untouched, otherwise dispatch calls would
 * stack up and double-fire.
 */
public final class HookInjectionTracker {

    private final Set<String> injected = ConcurrentHashMap.newKeySet();

    /** Build the canonical tracking key. */
    public static String key(String classInternal, String methodName, String descriptor) {
        return classInternal + "#" + methodName + descriptor;
    }

    /** True when this exact method has been injected before. */
    public boolean alreadyInjected(String classInternal, String methodName, String descriptor) {
        return injected.contains(key(classInternal, methodName, descriptor));
    }

    /**
     * Try to claim a method for injection.
     *
     * @return true when the caller owns the injection (first claimant);
     *         false when another pass already injected it.
     */
    public boolean tryClaim(String classInternal, String methodName, String descriptor) {
        return injected.add(key(classInternal, methodName, descriptor));
    }

    /** Release a claim after a failed transform so later passes may retry. */
    public void release(String classInternal, String methodName, String descriptor) {
        injected.remove(key(classInternal, methodName, descriptor));
    }

    public int size() {
        return injected.size();
    }
}
