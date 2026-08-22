package dev.blockconnect.bcagent.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class McVersionDetectorTest {

    @TempDir
    Path tempDir;

    @Test
    void testToProfileMappings() {
        assertEquals("26", McVersionDetector.toProfile("26.2"));
        assertEquals("26", McVersionDetector.toProfile("26.1.2"));
        assertEquals("1.21", McVersionDetector.toProfile("1.21.1"));
        assertEquals("1.21", McVersionDetector.toProfile("1.21"));
        assertEquals("1.20", McVersionDetector.toProfile("1.20.4"));
        assertNull(McVersionDetector.toProfile("1.12.2"));
        assertNull(McVersionDetector.toProfile(null));
        assertNull(McVersionDetector.toProfile("  "));
    }

    @Test
    void testSyspropOverrideTakesPrecedence(@TempDir Path cwd) throws IOException {
        Files.writeString(cwd.resolve("version.json"), "{\"id\":\"1.20.1\"}");
        System.setProperty("bcdebug.mcversion", "26.2");
        try {
            assertEquals("26.2", McVersionDetector.detect(cwd));
        } finally {
            System.clearProperty("bcdebug.mcversion");
        }
    }

    @Test
    void testDetectFromGameRootVersionJson(@TempDir Path cwd) throws IOException {
        Files.writeString(cwd.resolve("version.json"),
            "{\"id\":\"1.21.1\",\"name\":\"1.21.1\",\"type\":\"release\"}");
        assertEquals("1.21.1", McVersionDetector.detect(cwd));
        assertEquals("1.21", McVersionDetector.toProfile(McVersionDetector.detect(cwd)));
    }

    @Test
    void testDetectFromVersionsDirectory(@TempDir Path cwd) throws IOException {
        Path inst = cwd.resolve("versions").resolve("1.20.4");
        Files.createDirectories(inst);
        Files.writeString(inst.resolve("version.json"), "{\"id\":\"1.20.4\"}");
        assertEquals("1.20.4", McVersionDetector.detect(cwd));
    }

    @Test
    void testEmptyGameRootReturnsNull(@TempDir Path cwd) {
        assertNull(McVersionDetector.detect(cwd));
    }

    @Test
    void testMalformedJsonReturnsNull(@TempDir Path cwd) throws IOException {
        Files.writeString(cwd.resolve("version.json"), "{not json at all");
        assertNull(McVersionDetector.detect(cwd));
    }
}
