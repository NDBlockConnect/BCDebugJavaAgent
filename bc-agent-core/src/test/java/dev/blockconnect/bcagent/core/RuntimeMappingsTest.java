package dev.blockconnect.bcagent.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeMappingsTest {

    @TempDir
    Path tempDir;

    private Path writeMappings(String content) throws IOException {
        Path file = tempDir.resolve("server.txt");
        Files.writeString(file, content);
        return file;
    }

    @Test
    void parsesClassLinesAndTranslates() throws IOException {
        Path file = writeMappings(String.join("\n",
            "# comment line",
            "net.minecraft.server.level.ServerLevel -> arf:",
            "    void tick(BooleanSupplier) -> a(ZZ)V",
            "net.minecraft.client.Minecraft -> ezb:",
            "nonclass line without arrow suffix"));

        RuntimeMappings m = RuntimeMappings.load(file);
        assertEquals(2, m.size());
        assertEquals("arf", m.toRuntimeName("net/minecraft/server/level/ServerLevel"));
        assertEquals("arf", m.toRuntimeName("net.minecraft.server.level.ServerLevel"));
        assertEquals("ezb", m.toRuntimeName("net/minecraft/client/Minecraft"));
        assertTrue(m.hasMapping("net.minecraft.client.Minecraft"));
    }

    @Test
    void unmappedNamesPassThroughNormalized() throws IOException {
        RuntimeMappings m = RuntimeMappings.load(writeMappings(
            "a.b.C -> d:"));
        assertEquals("x/y/Z", m.toRuntimeName("x.y.Z"));
        assertFalse(m.hasMapping("x.y.Z"));
        assertNull(m.toRuntimeName(null));
        assertEquals("", m.toRuntimeName(""));
    }

    @Test
    void indentedMethodLinesAreIgnored() throws IOException {
        RuntimeMappings m = RuntimeMappings.load(writeMappings(String.join("\n",
            "net.minecraft.Util -> uv:",
            "    int getMillis() -> a()")));
        // The class line must register; the method line must not corrupt it
        assertEquals(1, m.size());
        assertTrue(m.hasMapping("net.minecraft.Util"));
    }

    @Test
    void methodTranslationWithDescriptorDisambiguation() throws IOException {
        Path file = writeMappings(String.join("\n",
            "net.minecraft.server.level.ServerLevel -> aqu:",
            "# {\"fileName\":\"ServerLevel.java\",\"id\":\"sourceFile\"}",
            "    303:419:void tick(java.util.function.BooleanSupplier) -> a",
            "    522:564:void tick(boolean) -> c",
            "    101:126:int getEntityCount() -> d"));
        RuntimeMappings m = RuntimeMappings.load(file);

        assertEquals("aqu", m.toRuntimeName("net/minecraft/server/level/ServerLevel"));
        assertEquals("a", m.toRuntimeMethodName(
            "net/minecraft/server/level/ServerLevel", "tick",
            "(Ljava/util/function/BooleanSupplier;)V"));
        assertEquals("c", m.toRuntimeMethodName(
            "net/minecraft/server/level/ServerLevel", "tick", "(Z)V"));
        assertEquals("d", m.toRuntimeMethodName(
            "net/minecraft/server/level/ServerLevel", "getEntityCount", "()I"));
        // Unknown methods pass through untouched
        assertEquals("tick", m.toRuntimeMethodName(
            "net/minecraft/server/level/ServerLevel", "tick", "(F)V"));
    }

    @Test
    void arrayAndQualifiedTypesConvertToJvmDescriptors() throws IOException {
        Path file = writeMappings(String.join("\n",
            "net.minecraft.world.level.Level -> eaq:",
            "    10:20:net.minecraft.world.entity.Entity[] gather(int[]) -> a"));
        RuntimeMappings m = RuntimeMappings.load(file);
        // Descriptor conversion: (int[]) → ([I) and return Entity[]
        assertEquals("a", m.toRuntimeMethodName(
            "net/minecraft/world/level/Level", "gather", "([I)[Lnet/minecraft/world/entity/Entity;"));
    }
}
