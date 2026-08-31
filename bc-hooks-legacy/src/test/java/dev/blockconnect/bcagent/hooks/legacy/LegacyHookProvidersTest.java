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
        assertEquals(8, hooks.size());

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

        // Client-diagnosis hooks: setScreen + pauseGame
        boolean hasSetScreen = hooks.stream().anyMatch(h ->
            "setScreen".equals(h.methodName)
                && "(Lnet/minecraft/client/gui/screens/Screen;)V".equals(h.descriptor));
        assertTrue(hasSetScreen, "Minecraft.setScreen(Screen) hook missing");
        boolean hasPauseGame = hooks.stream().anyMatch(h ->
            "pauseGame".equals(h.methodName) && "(Z)V".equals(h.descriptor));
        assertTrue(hasPauseGame, "Minecraft.pauseGame(Z) hook missing");
    }

    @Test
    void mc120ProviderProfileAndHooks() {
        MC120HookProvider provider = new MC120HookProvider();
        assertEquals("MC-1.20-Hooks", provider.name());
        assertTrue(provider.matchesProfile("1.20"));
        assertFalse(provider.matchesProfile("1.21"));
        assertFalse(provider.matchesProfile("26"));

        assertEquals(8, provider.getHooks().size());
    }

    @Test
    void providersAreExclusiveByProfile() {
        MC120HookProvider p120 = new MC120HookProvider();
        MC121HookProvider p121 = new MC121HookProvider();
        MC112HookProvider p112 = new MC112HookProvider();
        // Same profile string must never activate two providers
        for (String profile : new String[]{"26", "1.21", "1.20", "1.12", "auto", ""}) {
            int active = (p120.matchesProfile(profile) ? 1 : 0)
                       + (p121.matchesProfile(profile) ? 1 : 0)
                       + (p112.matchesProfile(profile) ? 1 : 0);
            assertTrue(active <= 1, "Multiple providers match profile: " + profile);
        }
    }

    @Test
    void mc112ExperimentalProviderProfileAndHooks() {
        MC112HookProvider provider = new MC112HookProvider();
        assertEquals("MC-1.12-Hooks (experimental)", provider.name());
        assertTrue(provider.matchesProfile("1.12"));
        assertFalse(provider.matchesProfile("1.21"));
        assertFalse(provider.matchesProfile("1.20"));
        assertFalse(provider.matchesProfile("26"));
        assertFalse(provider.matchesProfile("auto"));

        List<MethodHook> hooks = provider.getHooks();
        assertEquals(5, hooks.size());

        // MCP-era signatures: runTick()V (no boolean), render(FJ)V
        assertTrue(hooks.stream().anyMatch(h ->
            "runTick".equals(h.methodName) && "()V".equals(h.descriptor)));
        assertTrue(hooks.stream().anyMatch(h ->
            "render".equals(h.methodName) && "(FJ)V".equals(h.descriptor)));
        assertTrue(hooks.stream().anyMatch(h ->
            "loadWorld".equals(h.methodName)
                && "(Lnet/minecraft/client/multiplayer/WorldClient;)V".equals(h.descriptor)));
    }
}
