package dev.blockconnect.bcagent.core;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which classes the general recorder has already instrumented, so a
 * later {@code retransformClasses} pass can safely instrument classes that
 * were OUT of scope at load time (live filter extension) while leaving
 * already-instrumented bytes untouched (no double counting).
 */
public final class InstrumentationTracker {

    private final Set<String> instrumented = ConcurrentHashMap.newKeySet();

    /** True when this class was instrumented in an earlier pass. */
    public boolean alreadyInstrumented(String classInternal) {
        return instrumented.contains(classInternal);
    }

    /** Try to claim a class for instrumentation (first claimant wins). */
    public boolean tryClaim(String classInternal) {
        return instrumented.add(classInternal);
    }

    /** Release a claim after a failed transform so later passes may retry. */
    public void release(String classInternal) {
        instrumented.remove(classInternal);
    }

    public int size() {
        return instrumented.size();
    }
}
