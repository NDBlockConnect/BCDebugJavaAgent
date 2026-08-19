package dev.blockconnect.bcagent.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentLoggerTest {

    @Test
    void testLogLevels() {
        AgentLogger logger = AgentLogger.getInstance();
        assertNotNull(logger);

        AgentConfig config = AgentConfig.parse("logLevel=TRACE");
        AgentLogger.init(config);

        int before = logger.getRecordCount();
        logger.trace("test trace");
        logger.debug("test debug");
        logger.info("test info");
        logger.warn("test warn");
        logger.error("test error");

        assertTrue(logger.getRecordCount() >= before + 5);
    }

    @Test
    void testSnapshot() {
        AgentLogger logger = AgentLogger.getInstance();
        AgentConfig config = AgentConfig.parse("logLevel=DEBUG");
        AgentLogger.init(config);

        logger.info("snapshot test message");
        AgentLogger.LogRecord[] snapshot = logger.snapshot();

        assertNotNull(snapshot);
        assertTrue(snapshot.length > 0);

        boolean found = false;
        for (AgentLogger.LogRecord rec : snapshot) {
            if (rec.message != null && rec.message.contains("snapshot test message")) {
                found = true;
                assertEquals(AgentLogger.Level.INFO, rec.level);
                break;
            }
        }
        assertTrue(found, "Should find the logged message in snapshot");
    }

    @Test
    void testLevelFromString() {
        assertEquals(AgentLogger.Level.TRACE, AgentLogger.Level.fromString("TRACE"));
        assertEquals(AgentLogger.Level.DEBUG, AgentLogger.Level.fromString("debug"));
        assertEquals(AgentLogger.Level.INFO, AgentLogger.Level.fromString("Info"));
        assertEquals(AgentLogger.Level.INFO, AgentLogger.Level.fromString("invalid"));
    }
}
