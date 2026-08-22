package dev.blockconnect.bcagent.hooks.legacy;

/**
 * Hook provider for Minecraft 1.20.x (Mojang official mappings).
 * Runs on JDK 17 game JVMs (agent bytecode targets release 17).
 */
public class MC120HookProvider extends LegacyHookProviderBase {

    public MC120HookProvider() {
        super("1.20");
    }

    @Override
    protected String profile() {
        return "1.20";
    }

    @Override
    public String name() {
        return "MC-1.20-Hooks";
    }
}
