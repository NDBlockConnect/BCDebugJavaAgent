package dev.blockconnect.bcagent.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentConfigTest {

    @Test
    void testDefaultConfig() {
        AgentConfig config = AgentConfig.parse(null);
        assertEquals("bcdebug-output", config.outputDir);
        assertTrue(config.logMethodEntry);
        assertFalse(config.logMethodExit);
        assertTrue(config.enableHooks);
        assertEquals("auto", config.hookProfile);
        assertEquals("INFO", config.logLevel);
    }

    @Test
    void testParseArgs() {
        AgentConfig config = AgentConfig.parse(
            "outputDir=test-output,logLevel=DEBUG,logMethodExit=true,hookProfile=26");
        assertEquals("test-output", config.outputDir);
        assertEquals("DEBUG", config.logLevel);
        assertTrue(config.logMethodExit);
        assertEquals("26", config.hookProfile);
    }

    @Test
    void testClassFilters() {
        AgentConfig config = AgentConfig.parse("classFilters=net.minecraft.;com.mojang.");
        assertTrue(config.matchesClass("net.minecraft.client.Minecraft"));
        assertTrue(config.matchesClass("com.mojang.blaze3d.systems.RenderSystem"));
        assertFalse(config.matchesClass("dev.blockconnect.Test"));
    }

    @Test
    void testExcludeFilters() {
        AgentConfig config = AgentConfig.parse("excludefilters=net.minecraft.client.main.Main");
        assertFalse(config.matchesClass("net.minecraft.client.main.Main"));
        assertTrue(config.matchesClass("net.minecraft.client.Minecraft"));
    }
}
