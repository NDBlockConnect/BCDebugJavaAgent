package dev.blockconnect.bcagent.hooks.legacy;

/**
 * Hook provider for Minecraft 1.21.x (Mojang official mappings).
 * Runs on JDK 21 game JVMs.
 */
public class MC121HookProvider extends LegacyHookProviderBase {

    public MC121HookProvider() {
        super("1.21");
    }

    @Override
    protected String profile() {
        return "1.21";
    }

    @Override
    public String name() {
        return "MC-1.21-Hooks";
    }
}
