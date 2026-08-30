package dev.blockconnect.bcagent.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * ProGuard mapping reader supporting class and method sections.
 * <p>
 * Mojang publishes per-version mapping files ({@code client.txt} /
 * {@code server.txt}) in ProGuard format:
 *
 * <pre>
 * net.minecraft.server.level.ServerLevel -> arf:
 *     void tick(BooleanSupplier) -> b(BooleanSupplier):
 * </pre>
 *
 * Hook targets are authored against Mojang (deobfuscated) names, while stock
 * legacy jars (1.20.x / 1.21.x) define obfuscated runtime names for classes
 * <em>and methods</em>. This class translates both, using JVM method
 * descriptors as the overload-disambiguation key.
 */
public final class RuntimeMappings {

    /** deobf internal class name -> runtime internal class name. */
    private final Map<String, String> classMap = new HashMap<>();

    /** Lazily built reverse view: runtime class name -> deobf name. */
    private volatile Map<String, String> reverseClassMap;

    /**
     * Lazily built reverse method view:
     * runtime internal class name -> (runtimeMethodName|jvmDescriptor -> deobfMethodName).
     */
    private volatile Map<String, Map<String, String>> reverseMethodMap;

    /**
     * deobf internal class name -> (methodLookupKey -> runtime method name),
     * where methodLookupKey = {@code deobfName + "|" + jvmDescriptor}.
     */
    private final Map<String, Map<String, String>> methodMap = new HashMap<>();

    private static final Map<String, String> PRIMITIVES = buildPrimitives();

    private static Map<String, String> buildPrimitives() {
        Map<String, String> m = new HashMap<>();
        m.put("void", "V");
        m.put("boolean", "Z");
        m.put("byte", "B");
        m.put("short", "S");
        m.put("char", "C");
        m.put("int", "I");
        m.put("long", "J");
        m.put("float", "F");
        m.put("double", "D");
        return m;
    }

    private RuntimeMappings() {}

    /** Parse a ProGuard mapping file (class + method lines). */
    public static RuntimeMappings load(Path file) throws IOException {
        RuntimeMappings mappings = new RuntimeMappings();
        String currentClass = null;

        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (raw.trim().isEmpty() || raw.startsWith("#")) continue;

            if (!Character.isWhitespace(raw.charAt(0))) {
                int arrow = raw.indexOf("->");
                if (arrow <= 0 || !raw.endsWith(":")) { currentClass = null; continue; }
                String deobf = raw.substring(0, arrow).trim();
                String runtime = raw.substring(arrow + 2, raw.length() - 1).trim();
                if (deobf.isEmpty() || runtime.isEmpty()) { currentClass = null; continue; }
                currentClass = deobf.replace('.', '/');
                mappings.classMap.put(currentClass, runtime.replace('.', '/'));
            } else if (currentClass != null) {
                parseMethodLine(mappings, currentClass, raw.trim());
            }
        }
        return mappings;
    }

    /** Pre-compiled pattern for Mojang/ProGuard method lines, e.g.
     *  {@code 303:419:void tick(java.util.function.BooleanSupplier) -> a}
     *  (line-number prefix and trailing obf-argument list both optional). */
    private static final java.util.regex.Pattern METHOD_LINE = java.util.regex.Pattern.compile(
        "^\\s*(?:(\\d+):(\\d+):)?\\s*(\\S+)\\s+(\\S+)\\((.*)\\)\\s*->\\s*([^\\s(]+)");

    /** Parse {@code void tick(BooleanSupplier) -> b} / {@code 1:2:int f() -> a}. */
    private static void parseMethodLine(RuntimeMappings m, String classInternal,
                                         String line) {
        java.util.regex.Matcher match = METHOD_LINE.matcher(line);
        if (!match.matches()) return;

        String returnType = match.group(3);
        String methodName = match.group(4);
        String argsCsv = match.group(5);
        String obfName = match.group(6);
        if (returnType.isEmpty() || methodName.isEmpty() || obfName.isEmpty()) return;

        String jvmDescriptor = "(" + toDescriptorArgs(argsCsv.trim()) + ")"
            + toDescriptorType(returnType);

        m.methodMap.computeIfAbsent(classInternal, k -> new HashMap<>())
            .put(methodName + "|" + jvmDescriptor, obfName);
    }

    /** Convert a comma-separated Java-style argument list to JVM form. */
    private static String toDescriptorArgs(String csv) {
        if (csv.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String arg : splitTopLevel(csv)) {
            sb.append(toDescriptorType(arg.trim()));
        }
        return sb.toString();
    }

    private static String toDescriptorType(String type) {
        String t = type.trim();
        int arrays = 0;
        while (t.endsWith("[]")) { arrays++; t = t.substring(0, t.length() - 2).trim(); }
        String base = PRIMITIVES.getOrDefault(t,
            t.contains(".") || t.contains("/")
                ? "L" + t.replace('.', '/') + ";" : null);
        if (base == null) {
            // Unqualified or unknown — assume reference in default package or
            // an unresolvable shorthand; keep it distinguishable either way.
            base = "L" + t.replace('.', '/') + ";";
        }
        StringBuilder prefix = new StringBuilder();
        for (int i = 0; i < arrays; i++) prefix.append('[');
        return prefix + base;
    }

    /** Split on commas not nested inside angle brackets. */
    private static java.util.List<String> splitTopLevel(String csv) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        int depth = 0, start = 0;
        for (int i = 0; i < csv.length(); i++) {
            char c = csv.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (c == ',' && depth == 0) {
                parts.add(csv.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(csv.substring(start));
        return parts;
    }

    /** Number of parsed class mappings. */
    public int size() {
        return classMap.size();
    }

    /**
     * Translate a deobfuscated class name (dot or slash form) to the runtime
     * name in slash-internal format. Returns the input normalized to slashes
     * when no mapping exists.
     */
    public String toRuntimeName(String deobfClassName) {
        if (deobfClassName == null || deobfClassName.trim().isEmpty()) return deobfClassName;
        String key = deobfClassName.replace('.', '/');
        return classMap.getOrDefault(key, key);
    }

    /**
     * Translate a deobfuscated method name for the given deobfuscated class
     * and JVM descriptor. Returns the input when no mapping exists.
     */
    public String toRuntimeMethodName(String deobfClassName, String deobfMethodName,
                                       String jvmDescriptor) {
        if (deobfClassName == null || deobfMethodName == null) return deobfMethodName;
        Map<String, String> methods =
            methodMap.get(deobfClassName.replace('.', '/'));
        if (methods == null) return deobfMethodName;
        return methods.getOrDefault(deobfMethodName + "|" + jvmDescriptor,
            deobfMethodName);
    }

    /** True when the given deobfuscated class name has a runtime mapping. */
    public boolean hasMapping(String deobfClassName) {
        if (deobfClassName == null) return false;
        return classMap.containsKey(deobfClassName.replace('.', '/'));
    }

    /**
     * Reverse lookup for display/export: translate a runtime (possibly
     * obfuscated) internal name back to its Mojang name. Returns the input
     * normalized to slashes when no reverse mapping exists.
     */
    public String toDeobfName(String runtimeClassName) {
        if (runtimeClassName == null || runtimeClassName.trim().isEmpty()) {
            return runtimeClassName;
        }
        Map<String, String> reverse = this.reverseClassMap;
        if (reverse == null) {
            synchronized (this) {
                reverse = reverseClassMap;
                if (reverse == null) {
                    reverse = new HashMap<>(classMap.size() * 2);
                    for (Map.Entry<String, String> e : classMap.entrySet()) {
                        reverse.putIfAbsent(e.getValue(), e.getKey());
                    }
                    reverseClassMap = reverse;
                }
            }
        }
        String key = runtimeClassName.replace('.', '/');
        return reverse.getOrDefault(key, key);
    }

    /**
     * Reverse method lookup for display/export: translate a runtime method
     * name back to its Mojang name using the runtime class and the JVM
     * descriptor. Returns the input when no mapping exists.
     */
    public String toDeobfMethodName(String runtimeClassName, String runtimeMethodName,
                                      String jvmDescriptor) {
        if (runtimeClassName == null || runtimeMethodName == null) return runtimeMethodName;
        String key = runtimeClassName.replace('.', '/');
        if (!classMap.containsValue(key)) return runtimeMethodName;

        Map<String, Map<String, String>> rev = this.reverseMethodMap;
        if (rev == null) {
            synchronized (this) {
                rev = reverseMethodMap;
                if (rev == null) {
                    rev = new HashMap<>(methodMap.size() * 2);
                    for (Map.Entry<String, Map<String, String>> byClass
                            : methodMap.entrySet()) {
                        String runtimeClass = classMap.get(byClass.getKey());
                        if (runtimeClass == null) continue;
                        Map<String, String> bucket = rev.computeIfAbsent(
                            runtimeClass, k -> new HashMap<>());
                        for (Map.Entry<String, String> e : byClass.getValue().entrySet()) {
                            // forward key: deobfName|desc -> obfName
                            int bar = e.getKey().indexOf('|');
                            String desc = bar >= 0 ? e.getKey().substring(bar + 1) : "";
                            bucket.putIfAbsent(e.getValue() + "|" + desc, e.getKey());
                        }
                    }
                    reverseMethodMap = rev;
                }
            }
        }
        Map<String, String> bucket = rev.get(key);
        if (bucket == null) return runtimeMethodName;
        String forward = bucket.get(runtimeMethodName + "|" + jvmDescriptor);
        return forward != null ? forward.substring(0, forward.indexOf('|'))
            : runtimeMethodName;
    }
}
