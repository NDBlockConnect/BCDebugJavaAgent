package dev.blockconnect.bcagent.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HookRegistryRuntimeTest {

    @Test
    void runtimeHookLifecycle() {
        HookRegistry registry = HookRegistry.getInstance();
        int before = registry.totalHooks();

        registry.addRuntimeHook("com/mojang/brigadier/StringReader",
            "canRead", "()Z", "INFO");

        assertEquals(before + 1, registry.totalHooks());
        List<MethodHook> runtime = registry.getRuntimeHooks();
        assertEquals(1, runtime.size());
        assertEquals("com/mojang/brigadier/StringReader", runtime.get(0).className);
        assertEquals("canRead", runtime.get(0).methodName);
        assertEquals("()Z", runtime.get(0).descriptor);

        // dispatch path sees it
        assertNotNull(registry.getHooks("com/mojang/brigadier/StringReader"));

        assertEquals(1, registry.clearRuntimeHooks());
        assertEquals(before, registry.totalHooks());
        assertEquals(0, registry.getRuntimeHooks().size());
        assertNull(registry.getHooks("com/mojang/brigadier/StringReader"));
    }

    @Test
    void clearedRuntimeHookDispatchNoOps() {
        HookRegistry registry = HookRegistry.getInstance();
        registry.addRuntimeHook("probe/Class", "m", "()V", "INFO");
        registry.clearRuntimeHooks();
        // old call sites keep firing into dispatch — must no-op safely
        assertDoesNotThrow(() ->
            HookDispatcher.dispatchEntry("probe/Class", "m", "()V", new Object[0]));
    }
}
