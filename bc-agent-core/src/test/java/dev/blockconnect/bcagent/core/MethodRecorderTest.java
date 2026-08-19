package dev.blockconnect.bcagent.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

class MethodRecorderTest {

    @Test
    void testOnMethodEntry() {
        long start = MethodRecorder.onMethodEntry("TestEntryClass", "testMethod", "()V");
        assertTrue(start > 0);

        Map<String, MethodRecorder.MethodStats> stats = MethodRecorder.snapshot();
        assertTrue(stats.containsKey("TestEntryClass#testMethod()V"));
        assertTrue(stats.get("TestEntryClass#testMethod()V").entryCount.get() >= 1);
    }

    @Test
    void testOnMethodExit() throws InterruptedException {
        long start = MethodRecorder.onMethodEntry("TestExitClass", "testMethod", "()V");
        Thread.sleep(10);
        MethodRecorder.onMethodExit("TestExitClass", "testMethod", "()V", start);

        Map<String, MethodRecorder.MethodStats> stats = MethodRecorder.snapshot();
        MethodRecorder.MethodStats ms = stats.get("TestExitClass#testMethod()V");
        assertNotNull(ms);
        assertTrue(ms.exitCount.get() >= 1);
        assertTrue(ms.totalNanos.get() >= 10_000_000);
    }

    @Test
    void testOnMethodException() {
        long start = MethodRecorder.onMethodEntry("TestExceptionClass", "testMethod", "()V");
        MethodRecorder.onMethodException("TestExceptionClass", "testMethod", "()V", start);

        Map<String, MethodRecorder.MethodStats> stats = MethodRecorder.snapshot();
        MethodRecorder.MethodStats ms = stats.get("TestExceptionClass#testMethod()V");
        assertNotNull(ms);
        assertTrue(ms.totalNanos.get() >= 0);
    }

    @Test
    void testMultipleCalls() {
        String uniqueClass = "TestMultiClass_" + System.nanoTime();
        for (int i = 0; i < 5; i++) {
            long start = MethodRecorder.onMethodEntry(uniqueClass, "loopMethod", "()V");
            MethodRecorder.onMethodExit(uniqueClass, "loopMethod", "()V", start);
        }

        Map<String, MethodRecorder.MethodStats> stats = MethodRecorder.snapshot();
        MethodRecorder.MethodStats ms = stats.get(uniqueClass + "#loopMethod()V");
        assertNotNull(ms);
        assertEquals(5, ms.entryCount.get());
        assertEquals(5, ms.exitCount.get());
    }
}
