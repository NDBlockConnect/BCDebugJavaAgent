package dev.blockconnect.bcagent.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MethodRecorderMuteTest {

    @Test
    void mutedPrefixesStopRecordingButExitPathIsSafe() {
        String unique = "MuteProbe_" + System.nanoTime();
        MethodRecorder.setMutedPrefixes(new String[]{unique});

        long start = MethodRecorder.onMethodEntry(unique, "m", "()V");
        assertTrue(start > 0, "entry still returns a timestamp");
        MethodRecorder.onMethodExit(unique, "m", "()V", start);

        Map<String, MethodRecorder.MethodStats> stats = MethodRecorder.snapshot();
        assertNull(stats.get(unique + "#m()V"), "muted class must not gain stats");

        MethodRecorder.setMutedPrefixes(new String[0]);
    }

    @Test
    void unmuteRestoresRecording() {
        String unique = "MuteResume_" + System.nanoTime();
        MethodRecorder.setMutedPrefixes(new String[]{unique});
        long start = MethodRecorder.onMethodEntry(unique, "m", "()V");
        MethodRecorder.onMethodExit(unique, "m", "()V", start);
        assertFalse(MethodRecorder.snapshot().containsKey(unique + "#m()V"));

        MethodRecorder.setMutedPrefixes(new String[0]);
        start = MethodRecorder.onMethodEntry(unique, "m", "()V");
        MethodRecorder.onMethodExit(unique, "m", "()V", start);
        assertTrue(MethodRecorder.snapshot().containsKey(unique + "#m()V"),
            "recording resumes after unmuting");
    }

    @Test
    void nonMutedClassesRecordNormally() {
        String unique = "MuteOther_" + System.nanoTime();
        MethodRecorder.setMutedPrefixes(new String[]{"totally/different"});
        long start = MethodRecorder.onMethodEntry(unique, "m", "()V");
        MethodRecorder.onMethodExit(unique, "m", "()V", start);
        assertTrue(MethodRecorder.snapshot().containsKey(unique + "#m()V"));
        MethodRecorder.setMutedPrefixes(new String[0]);
    }
}
