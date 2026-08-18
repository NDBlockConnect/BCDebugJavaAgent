package dev.blockconnect.bcagent.core;

import java.util.List;

/**
 * SPI interface for hook providers.
 * <p>
 * Implementations are discovered via {@code META-INF/services/} and provide
 * version-specific method hooks for Minecraft.
 */
public interface HookProvider {

    /** Provider name (e.g. "MC-26-Hooks"). */
    String name();

    /** Whether this provider matches the given hook profile (e.g. "26", "auto"). */
    boolean matchesProfile(String profile);

    /** Register any setup with the instrumentation (e.g. additional transformers). */
    void register(java.lang.instrument.Instrumentation inst);

    /** Return the list of method hooks this provider defines. */
    List<MethodHook> getHooks();
}
