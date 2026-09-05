package dev.blockconnect.bcagent.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InstrumentationTrackerTest {

    @Test
    void claimIsExclusiveAndReleasable() {
        InstrumentationTracker tracker = new InstrumentationTracker();

        assertTrue(tracker.tryClaim("net/minecraft/client/Minecraft"));
        assertFalse(tracker.tryClaim("net/minecraft/client/Minecraft"), "double claim fails");
        assertTrue(tracker.alreadyInstrumented("net/minecraft/client/Minecraft"));

        tracker.release("net/minecraft/client/Minecraft");
        assertFalse(tracker.alreadyInstrumented("net/minecraft/client/Minecraft"));
        assertTrue(tracker.tryClaim("net/minecraft/client/Minecraft"), "releasable for retry");
        assertEquals(1, tracker.size());
    }

    @Test
    void innerClassesAreSeparateClaims() {
        InstrumentationTracker tracker = new InstrumentationTracker();
        assertTrue(tracker.tryClaim("aqu"));
        assertTrue(tracker.tryClaim("aqu$a"), "inner class = distinct claim");
        assertEquals(2, tracker.size());
    }
}
