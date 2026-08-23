package dev.blockconnect.bcagent.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HookInjectionTrackerTest {

    @Test
    void claimIsExclusiveAndReleasable() {
        HookInjectionTracker tracker = new HookInjectionTracker();
        String cls = "net/minecraft/server/level/ServerLevel";

        assertTrue(tracker.tryClaim(cls, "tick", "(Z)V"));
        assertFalse(tracker.tryClaim(cls, "tick", "(Z)V"), "second claim must fail");
        assertTrue(tracker.alreadyInjected(cls, "tick", "(Z)V"));

        tracker.release(cls, "tick", "(Z)V");
        assertFalse(tracker.alreadyInjected(cls, "tick", "(Z)V"));
        assertTrue(tracker.tryClaim(cls, "tick", "(Z)V"), "releasable for retry");
    }

    @Test
    void keysDistinguishOverloadsAndClasses() {
        HookInjectionTracker tracker = new HookInjectionTracker();
        String a = "com/A";
        String b = "com/B";

        assertTrue(tracker.tryClaim(a, "m", "(I)V"));
        assertTrue(tracker.tryClaim(a, "m", "(J)V"), "different descriptor = different key");
        assertTrue(tracker.tryClaim(b, "m", "(I)V"), "different class = different key");
        assertEquals(3, tracker.size());

        assertTrue(tracker.alreadyInjected(a, "m", "(I)V"));
        assertFalse(tracker.alreadyInjected(a, "n", "(I)V"));
    }
}
