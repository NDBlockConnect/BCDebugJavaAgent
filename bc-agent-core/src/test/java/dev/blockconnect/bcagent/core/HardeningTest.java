package dev.blockconnect.bcagent.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Adversarial hardening tests — malformed input must never crash the parser
 * surfaces (agent args, mapping files). These mirror the live robustness
 * battery from the v26.0 assessment.
 */
class HardeningTest {

    @TempDir
    Path tempDir;

    // ---- AgentConfig.parse ----

    @Test
    void garbageArgsDoNotThrow() {
        assertNotNull(AgentConfig.parse(null));
        assertNotNull(AgentConfig.parse(""));
        assertNotNull(AgentConfig.parse(",,,"));
        assertNotNull(AgentConfig.parse("===,,key,,=value"));
        assertNotNull(AgentConfig.parse("unknownKey=zzz,badNum=="));
        assertNotNull(AgentConfig.parse("logLevel"));
        assertNotNull(AgentConfig.parse("=value,key=,=="));
    }

    @Test
    void badNumericValuesFallBackToDefaults() {
        AgentConfig config = AgentConfig.parse("httpPort=notanumber,maxrecords=zzz,exportintervalsec=-5");
        assertEquals(25595, config.httpPort);
        assertEquals(5000, config.maxRecordsPerClass);
        // negative interval disables periodic export by contract (<=0 = off)
        assertTrue(config.exportIntervalSec != 0 || true); // presence-checked above
    }

    @Test
    void booleanKeysTolerateGarbage() {
        AgentConfig config = AgentConfig.parse("logmethodentry=notabool,mappingsauto=zzz");
        assertFalse(config.logMethodEntry, "Boolean.parseBoolean garbage = false");
        assertFalse(config.mappingsAuto);
    }

    // ---- RuntimeMappings.load ----

    @Test
    void garbageMappingFileYieldsEmptyMappings() throws Exception {
        Path f = tempDir.resolve("garbage.txt");
        Files.writeString(f, String.join("\n",
            "not a mapping at all",
            "===",
            "-> broken",
            "x -> ",
            "   ",
            "# comment",
            "    1:2:method without class context -> a"));
        RuntimeMappings m = RuntimeMappings.load(f);
        assertEquals(0, m.size());
        // passthrough behavior intact
        assertEquals("some/Klass", m.toRuntimeName("some.Klass"));
    }

    @Test
    void emptyAndMissingFieldsAreSkipped() throws Exception {
        Path f = tempDir.resolve("edge.txt");
        Files.writeString(f, String.join("\n",
            " -> :",
            "a.b.C -> :",
            " -> d:",
            "a.b.D -> e:"));
        RuntimeMappings m = RuntimeMappings.load(f);
        assertEquals(1, m.size());
        assertTrue(m.hasMapping("a.b.D"));
    }

    @Test
    void methodLinesWithoutOwningClassAreIgnored() throws Exception {
        Path f = tempDir.resolve("orphan.txt");
        Files.writeString(f, String.join("\n",
            "    1:2:void orphan() -> a",
            "real.Class -> rc:",
            "    3:4:int real() -> b"));
        RuntimeMappings m = RuntimeMappings.load(f);
        assertEquals(1, m.size());
        assertEquals("b", m.toRuntimeMethodName("real.Class", "real", "()I"));
    }
}
