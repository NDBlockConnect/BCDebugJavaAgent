package dev.blockconnect.bcagent.hooks.legacy;

import dev.blockconnect.bcagent.core.MethodHook;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LegacyHookProvidersTest {

    @Test
    void mc121ProviderProfileAndHooks() {
        MC121HookProvider provider = new MC121HookProvider();
        assertEquals("MC-1.21-Hooks", provider.name());
        assertTrue(provider.matchesProfile("1.21"));
        assertFalse(provider.matchesProfile("26"));
        assertFalse(provider.matchesProfile("auto"));
        assertFalse(provider.matchesProfile("1.20"));

        List<MethodHook> hooks = provider.getHooks();
        assertEquals(6, hooks.size());

        // ServerLevel.tick takes a BooleanSupplier in 1.20.x/1.21.x
        boolean hasServerTick = hooks.stream().anyMatch(h ->
            "net/minecraft/server/level/ServerLevel".equals(h.className)
                && "tick".equals(h.methodName)
                && "(Ljava/util/function/BooleanSupplier;)V".equals(h.descriptor));
        assertTrue(hasServerTick, "ServerLevel.tick(BooleanSupplier) hook missing");

        // runTick(boolean) signature
        boolean hasRunTick = hooks.stream().anyMatch(h ->
            "runTick".equals(h.methodName) && "(Z)V".equals(h.descriptor));
        assertTrue(hasRunTick, "Minecraft.runTick(Z) hook missing");
    }

    @Test
    void mc120ProviderProfileAndHooks() {
        MC120HookProvider provider = new MC120HookProvider();
        assertEquals("MC-1.20-Hooks", provider.name());
        assertTrue(provider.matchesProfile("1.20"));
        assertFalse(provider.matchesProfile("1.21"));
        assertFalse(provider.matchesProfile("26"));

        List<MethodHook> hooks = provider.getHooks();
        assertEquals(6, hooks.size());
    }

    @Test
    void providersAreExclusiveByProfile() {
        MC120HookProvider p120 = new MC120HookProvider();
        MC121HookProvider p121 = new MC121HookProvider();
        // Same profile string must never activate both providers
        for (String profile : new String[]{"26", "1.21", "1.20", "auto", ""}) {
            int active = (p120.matchesProfile(profile) ? 1 : 0)
                       + (p121.matchesProfile(profile) ? 1 : 0);
            assertTrue(active <= 1, "Both providers match profile: " + profile);
        }
    }
}
