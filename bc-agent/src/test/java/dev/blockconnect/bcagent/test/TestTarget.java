package dev.blockconnect.bcagent.test;

/**
 * Minimal test target — a class with methods for the agent to instrument.
 */
public class TestTarget {

    public static void main(String[] args) throws Exception {
        System.out.println("=== BCDebugJavaAgent Test ===");

        // Call some methods so the agent can record them
        for (int i = 0; i < 5; i++) {
            doWork(i);
        }

        Thread.sleep(500);

        System.out.println("=== Test complete ===");
        System.out.println("Check bcdebug-output/ for exported logs");
    }

    static void doWork(int iteration) {
        long sum = 0;
        for (int i = 0; i < 1000; i++) {
            sum += i * iteration;
        }
        System.out.println("Iteration " + iteration + " sum=" + sum);
    }
}
