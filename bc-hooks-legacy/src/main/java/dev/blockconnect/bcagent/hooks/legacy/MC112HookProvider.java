package dev.blockconnect.bcagent.hooks.legacy;

import dev.blockconnect.bcagent.core.AgentLogger;
import dev.blockconnect.bcagent.core.MethodHook;

import java.lang.instrument.Instrumentation;
import java.util.List;

/**
 * EXPERIMENTAL hook provider for Minecraft 1.12.2, authored against MCP
 * stable names.
 * <p>
 * The 1.12 era has NO official Mojang mappings: production jars run with
 * obfuscated class names and (under Forge) SRG method names. To make these
 * hooks match at runtime, users MUST supply a ProGuard-format mapping file
 * converted from MCP (class and method sections) via
 * {@code mappingsFile=...}. {@code mappingsAuto} cannot help here —
 * piston-meta publishes nothing for 1.12.
 * <p>
 * Hook set (MCP names, 1.12.2):
 * <ul>
 *   <li>{@code Minecraft.runTick()} — client tick loop</li>
 *   <li>{@code Minecraft.loadWorld(WorldClient)} — world load/unload</li>
 *   <li>{@code WorldClient.tick()} — client world tick</li>
 *   <li>{@code GameRenderer.render(float, long)} — render frame</li>
 *   <li>{@code MinecraftServer.tick()} — server tick loop</li>
 * </ul>
 * Signatures are best-effort for 1.12.2; mismatches simply never fire and
 * are visible through the registry counts.
 */
public class MC112HookProvider extends LegacyHookProviderBase {

    public MC112HookProvider() {
        super("1.12");
    }

    @Override
    protected String profile() {
        return "1.12";
    }

    @Override
    protected void addHooks(List<MethodHook> hooks) {
        hooks.add(new MethodHook(
            "net/minecraft/client/Minecraft", "runTick", "()V",
            (cls, name, desc, args, result, exc) ->
                AgentLogger.getInstance().debug("MC 1.12 tick loop"),
            null, false,
            "Client tick loop"
        ));

        hooks.add(new MethodHook(
            "net/minecraft/client/Minecraft", "loadWorld",
            "(Lnet/minecraft/client/multiplayer/WorldClient;)V",
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

        hooks.add(new MethodHook(
            "net/minecraft/client/multiplayer/WorldClient", "tick", "()V",
            (cls, name, desc, args, result, exc) ->
                AgentLogger.getInstance().trace("WorldClient tick"),
            null, false,
            "Client world tick"
        ));

        hooks.add(new MethodHook(
            "net/minecraft/client/renderer/GameRenderer", "render", "(FJ)V",
            (cls, name, desc, args, result, exc) ->
                AgentLogger.getInstance().trace("GameRenderer render frame"),
            null, false,
            "Render frame timing"
        ));

        hooks.add(new MethodHook(
            "net/minecraft/server/MinecraftServer", "tick", "()V",
            (cls, name, desc, args, result, exc) ->
                AgentLogger.getInstance().debug("Server tick loop"),
            null, false,
            "Server tick loop"
        ));
    }

    @Override
    public void register(Instrumentation inst) {
        AgentLogger.getInstance().warn(
            "MC-1.12-Hooks is EXPERIMENTAL: supply an MCP-derived ProGuard "
            + "mapping via mappingsFile=..., otherwise targets stay unmapped");
    }

    @Override
    public String name() {
        return "MC-1.12-Hooks (experimental)";
    }
}
