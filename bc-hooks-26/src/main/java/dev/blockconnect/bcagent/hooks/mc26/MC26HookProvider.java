package dev.blockconnect.bcagent.hooks.mc26;

import dev.blockconnect.bcagent.core.*;

import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.List;

/**
 * Hook provider for Minecraft 26.x (26.2, 26.1.2).
 * <p>
 * Registers hooks on key MC client lifecycle methods to record tick events,
 * world load/unload, and render frame timing.
 *
 * <h3>Target classes (Mojang official mappings, 26.x)</h3>
 * <ul>
 *   <li>{@code net.minecraft.client.Minecraft} — main client class</li>
 *   <li>{@code net.minecraft.client.multiplayer.ClientLevel} — client world</li>
 *   <li>{@code net.minecraft.server.level.ServerLevel} — server world</li>
 *   <li>{@code net.minecraft.client.renderer.GameRenderer} — render loop</li>
 * </ul>
 */
public class MC26HookProvider implements HookProvider {

    private final List<MethodHook> hooks = new ArrayList<>();

    public MC26HookProvider() {
        // Minecraft.tick() — main client tick
        hooks.add(new MethodHook(
            "net/minecraft/client/Minecraft", "tick", "()V",
            (cls, name, desc, args, result, exc) -> {
                AgentLogger.getInstance().debug("MC tick");
            },
            null, false,
            "Client tick lifecycle"
        ));

        // Minecraft.runTick() — tick loop iteration
        hooks.add(new MethodHook(
            "net/minecraft/client/Minecraft", "runTick", "()V",
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
            (cls, name, desc, args, result, exc) -> {
                AgentLogger.getInstance().trace("ClientLevel.tickEntities enter");
            },
            null, false,
            "Client world entity tick"
        ));

        // GameRenderer.render() — render frame
        hooks.add(new MethodHook(
            "net/minecraft/client/renderer/GameRenderer", "render", "(FJZ)V",
            (cls, name, desc, args, result, exc) -> {
                // args[0] = partialTick (float), args[1] = nanoTime (long)
                AgentLogger.getInstance().trace("GameRenderer.render enter");
            },
            null, false,
            "Render frame timing"
        ));

        // ServerLevel.tick() — server world tick
        hooks.add(new MethodHook(
            "net/minecraft/server/level/ServerLevel", "tick", "()V",
            (cls, name, desc, args, result, exc) -> {
                AgentLogger.getInstance().debug("ServerLevel tick");
            },
            null, false,
            "Server world tick"
        ));

        // Minecraft.setLevel() — world load/unload
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

        // Minecraft.setScreen(Screen) — GUI transitions (client diagnosis)
        hooks.add(new MethodHook(
            "net/minecraft/client/Minecraft", "setScreen",
            "(Lnet/minecraft/client/gui/screens/Screen;)V",
            (cls, name, desc, args, result, exc) -> {
                Object screen = args != null ? args[0] : null;
                AgentLogger.getInstance().info("Screen: "
                    + (screen == null ? "<null> (in-game HUD)" : screen.getClass().getSimpleName()));
            },
            null, false,
            "GUI screen transition tracking"
        ));

        // Minecraft.pauseGame(boolean) — pause state changes (client diagnosis)
        hooks.add(new MethodHook(
            "net/minecraft/client/Minecraft", "pauseGame", "(Z)V",
            (cls, name, desc, args, result, exc) -> {
                if (args != null && args.length > 0) {
                    AgentLogger.getInstance().info(
                        (Boolean.TRUE.equals(args[0])) ? "Game paused" : "Game resumed");
                }
            },
            null, false,
            "Pause state tracking"
        ));
    }

    @Override
    public String name() {
        return "MC-26-Hooks";
    }

    @Override
    public boolean matchesProfile(String profile) {
        return "26".equals(profile) || "auto".equals(profile);
    }

    @Override
    public void register(Instrumentation inst) {
        // No additional setup needed — hooks are registered via getHooks()
    }

    @Override
    public List<MethodHook> getHooks() {
        return hooks;
    }
}
