package dev.blockconnect.bcagent.core;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort detection of the running Minecraft version at agent startup.
 * <p>
 * Resolution order:
 * <ol>
 *   <li>System property {@code bcdebug.mcversion} (explicit override)</li>
 *   <li>System property {@code minecraft.version} (set by some launchers)</li>
 *   <li>{@code version.json} in the game root (official launcher layout)</li>
 *   <li>First {@code versions/<dir>/version.json} under the game root</li>
 * </ol>
 * Returns {@code null} when nothing can be resolved; callers then fall back to
 * their default profile behavior.
 */
public final class McVersionDetector {

    private static final Pattern ID_FIELD = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");

    private McVersionDetector() {}

    /** Detect the MC version using system properties and the current game root, or null. */
    public static String detect() {
        return detect(Paths.get("").toAbsolutePath());
    }

    /**
     * Detect the MC version under the given game root directory.
     * System properties still take precedence; file probing is scoped to
     * {@code root} (and {@code root/versions/*}).
     */
    public static String detect(Path root) {
        String v = System.getProperty("bcdebug.mcversion");
        if (v != null && !v.isBlank()) return v.trim();

        v = System.getProperty("minecraft.version");
        if (v != null && !v.isBlank()) return v.trim();

        try {
            v = parseId(root.resolve("version.json"));
            if (v != null) return v;

            Path versions = root.resolve("versions");
            if (Files.isDirectory(versions)) {
                try (DirectoryStream<Path> dirs = Files.newDirectoryStream(versions)) {
                    for (Path dir : dirs) {
                        if (!Files.isDirectory(dir)) continue;
                        // Official launcher layout: versions/<v>/version.json
                        v = parseId(dir.resolve("version.json"));
                        if (v != null) return v;
                        // Launcher layouts that name the manifest after the
                        // version dir (e.g. MDL): versions/<v>/<v>.json
                        v = parseId(dir.resolve(dir.getFileName().toString() + ".json"));
                        if (v != null) return v;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Detection is best-effort; never fail agent init here.
        }
        return null;
    }

    /**
     * Map a detected MC version string to a hook profile id
     * ("26", "1.21", "1.20"), or null when unmapped.
     */
    public static String toProfile(String mcVersion) {
        if (mcVersion == null || mcVersion.isBlank()) return null;
        String v = mcVersion.trim();
        if (v.startsWith("26.")) return "26";
        if (v.startsWith("1.21")) return "1.21";
        if (v.startsWith("1.20")) return "1.20";
        return null;
    }

    private static String parseId(Path json) {
        if (!Files.isRegularFile(json)) return null;
        try {
            String content = Files.readString(json);
            Matcher m = ID_FIELD.matcher(content);
            if (m.find()) {
                String id = m.group(1).trim();
                return id.isEmpty() ? null : id;
            }
        } catch (IOException ignored) {
        }
        return null;
    }
}
