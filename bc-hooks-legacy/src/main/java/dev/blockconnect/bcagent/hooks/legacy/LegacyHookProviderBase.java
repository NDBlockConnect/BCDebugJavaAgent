package dev.blockconnect.bcagent.hooks.legacy;

import dev.blockconnect.bcagent.core.*;

import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared hook definitions for legacy (pre-26.x) Minecraft versions using
 * Mojang official mappings. 1.20.x and 1.21.x expose identical signatures
 * for the instrumented lifecycle methods, so both providers extend this base.
 *
 * <h3>Target classes (Mojang official mappings, 1.20.x / 1.21.x)</h3>
 * <ul>
 *   <li>{@code net.minecraft.client.Minecraft} — main client class</li>
 *   <li>{@code net.minecraft.client.multiplayer.ClientLevel} — client world</li>
 *   <li>{@code net.minecraft.server.level.ServerLevel} — server world</li>
 *   <li>{@code net.minecraft.client.renderer.GameRenderer} — render loop</li>
 * </ul>
 */
public abstract class LegacyHookProviderBase implements HookProvider {

    private final String profileId;
    private final List<MethodHook> hooks;

    protected LegacyHookProviderBase(String profileId) {
        this.profileId = profileId;
        this.hooks = new ArrayList<>();
        registerHooks(hooks);
    }

    /** Profile identifier this provider responds to (e.g. "1.21"). */
    protected abstract String profile();

    @Override
    public final boolean matchesProfile(String profile) {
        return profile().equals(profile);
    }

    @Override
    public void register(Instrumentation inst) {
        // Hooks are delivered via getHooks(); no additional setup needed.
    }

    @Override
    public final List<MethodHook> getHooks() {
        return hooks;
    }

    private static void registerHooks(List<MethodHook> hooks) {
        // Minecraft.tick() — main client tick
        hooks.add(new MethodHook(
            "net/minecraft/client/Minecraft", "tick", "()V",
            (cls, name, desc, args, result, exc) ->
                AgentLogger.getInstance().debug("MC tick"),
            null, false,
            "Client tick lifecycle"
        ));

        // Minecraft.runTick(boolean) — tick loop iteration
        hooks.add(new MethodHook(
            "net/minecraft/client/Minecraft", "runTick", "(Z)V",
            null,
            (cls, name, desc, args, result, exc) -> {
                if (exc != null) {
                    AgentLogger.getInstance().error("MC runTick exception: " + exc.getMessage(), exc);
                }
            },
            true,
            "Client tick loop with exception capture"
        ));

        // ClientLevel.tickEntities() — entity tick
        hooks.add(new MethodHook(
            "net/minecraft/client/multiplayer/ClientLevel", "tickEntities", "()V",
            (cls, name, desc, args, result, exc) ->
                AgentLogger.getInstance().trace("ClientLevel.tickEntities enter"),
            null, false,
            "Client world entity tick"
        ));

        // GameRenderer.render(float, long, boolean) — render frame
        hooks.add(new MethodHook(
            "net/minecraft/client/renderer/GameRenderer", "render", "(FJZ)V",
            (cls, name, desc, args, result, exc) ->
                AgentLogger.getInstance().trace("GameRenderer.render enter"),
            null, false,
            "Render frame timing"
        ));

        // ServerLevel.tick(BooleanSupplier) — server world tick
        hooks.add(new MethodHook(
            "net/minecraft/server/level/ServerLevel", "tick",
            "(Ljava/util/function/BooleanSupplier;)V",
            (cls, name, desc, args, result, exc) ->
                AgentLogger.getInstance().debug("ServerLevel tick"),
            null, false,
            "Server world tick"
        ));

        // Minecraft.setLevel(ClientLevel) — world load/unload
        hooks.add(new MethodHook(
            "net/minecraft/client/Minecraft", "setLevel",
            "(Lnet/minecraft/client/multiplayer/ClientLevel;)V",
            (cls, name, desc, args, result, exc) -> {
                if (args != null && args[0] != null) {
                    AgentLogger.getInstance().info("World loaded: " + args[0]);
                } else {
                    AgentLogger.getInstance().info("World unloaded");
                }
            },
            null, false,
            "World load/unload tracking"
        ));
    }
}
