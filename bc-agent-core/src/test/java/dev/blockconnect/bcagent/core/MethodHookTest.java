package dev.blockconnect.bcagent.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MethodHookTest {

    @Test
    void testExactMatch() {
        MethodHook hook = new MethodHook(
            "net/minecraft/client/Minecraft", "tick", "()V",
            null, null, false, "test");

        assertTrue(hook.matches("net/minecraft/client/Minecraft", "tick", "()V"));
        assertFalse(hook.matches("net/minecraft/client/Minecraft", "runTick", "()V"));
        assertFalse(hook.matches("net/minecraft/client/Minecraft", "tick", "(I)V"));
        assertFalse(hook.matches("net/minecraft/server/MinecraftServer", "tick", "()V"));
    }

    @Test
    void testWildcardMethod() {
        MethodHook hook = new MethodHook(
            "net/minecraft/client/Minecraft", "*", "*",
            null, null, false, "all methods");

        assertTrue(hook.matches("net/minecraft/client/Minecraft", "tick", "()V"));
        assertTrue(hook.matches("net/minecraft/client/Minecraft", "runTick", "()V"));
        assertFalse(hook.matches("net/minecraft/server/MinecraftServer", "tick", "()V"));
    }

    @Test
    void testWildcardDescriptor() {
        MethodHook hook = new MethodHook(
            "net/minecraft/client/Minecraft", "tick", "*",
            null, null, false, "any tick");

        assertTrue(hook.matches("net/minecraft/client/Minecraft", "tick", "()V"));
        assertTrue(hook.matches("net/minecraft/client/Minecraft", "tick", "(I)V"));
    }
}
