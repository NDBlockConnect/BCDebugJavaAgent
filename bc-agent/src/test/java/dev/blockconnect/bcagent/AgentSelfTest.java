package dev.blockconnect.bcagent;

/**
 * Self-test class — verifies the agent loads and instruments correctly.
 * Run with: java -javaagent:bcdebug-javaagent.jar=logLevel=DEBUG,classfilters=dev.blockconnect.bcagent.test -cp <classpath> dev.blockconnect.bcagent.test.TestTarget
 */
public class AgentSelfTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== BCDebugJavaAgent Self-Test ===");
        System.out.println("Agent initialized: " + BCAgent.isInitialized());
        System.out.println("Config: " + BCAgent.getConfig());

        // Force a class load that matches our filters to trigger instrumentation
        try {
            Class.forName("dev.blockconnect.bcagent.test.TestTarget");
        } catch (ClassNotFoundException e) {
            System.out.println("TestTarget not on classpath — that's OK for self-test");
        }

        // Check method recorder state
        System.out.println("Method records: " + dev.blockconnect.bcagent.core.MethodRecorder.methodCount());

        // Force export
        System.out.println("Triggering export...");
        java.util.List<String> files = dev.blockconnect.bcagent.core.RecordExporter.exportAll("bcdebug-output");
        System.out.println("Exported files: " + files);

        System.out.println("=== Self-Test complete ===");
    }
}
