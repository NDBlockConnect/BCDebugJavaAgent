package dev.blockconnect.bcagent.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Automatic mapping acquisition for obfuscated legacy jars.
 * <p>
 * Resolution order:
 * <ol>
 *   <li>Explicit {@code mappingsFile} — used as-is.</li>
 *   <li>Cache hit — {@code <cacheDir>/<version>-<side>.txt} from a previous
 *       run is reused without any network traffic.</li>
 *   <li>Download from piston-meta: resolve the version entry in the version
 *       manifest, read its {@code server_mappings}/{@code client_mappings}
 *       download URL and fetch it into the cache.</li>
 * </ol>
 *
 * The side (client/server) is detected from the JVM launch command line:
 * dedicated servers are launched with {@code -jar server.jar}, clients carry a
 * {@code versions/<v>/<v>.jar} style classpath entry.
 */
public final class MappingAutoDiscovery {

    private static final String VERSION_MANIFEST =
        "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;

    private MappingAutoDiscovery() {}

    /** Whether automatic discovery should attempt network access. */
    public static boolean isServerSide() {
        String cmd = System.getProperty("sun.java.command", "");
        if (cmd.contains("server.jar")) return true;
        String cp = System.getProperty("java.class.path", "");
        if (cp.contains("server.jar")) return true;
        // Bundler-based servers relaunch through net.minecraft.server.Main on
        // a child loader while clients use net.minecraft.client.main.Main.
        String main = mainClassFromCommand(cmd);
        if (main.contains("server.Main")) return true;
        if (main.contains("client.main.Main")) return false;
        return cmd.toLowerCase().contains("nogui");
    }

    private static String mainClassFromCommand(String cmd) {
        // sun.java.command for -jar launches is "<jar> <args>"; for -cp
        // launches it is "<mainclass> <args>".
        String first = cmd.trim();
        int sp = first.indexOf(' ');
        if (sp > 0) first = first.substring(0, sp);
        return first;
    }

    /**
     * Resolve mappings for the given MC version. Returns the parsed mappings,
     * or null when unavailable (offline, unknown version, disabled).
     *
     * @param mcVersion  detected game version, e.g. "1.21.1"
     * @param cacheDir   directory for downloaded mappings (created on demand)
     */
    public static RuntimeMappings acquire(String mcVersion, Path cacheDir) throws IOException {
        boolean server = isServerSide();
        String side = server ? "server" : "client";
        Files.createDirectories(cacheDir);
        Path cached = cacheDir.resolve(mcVersion + "-" + side + ".txt");

        if (Files.isRegularFile(cached) && Files.size(cached) > 0) {
            AgentLogger.getInstance().info(
                "Mappings cache hit: " + cached.getFileName());
            return RuntimeMappings.load(cached);
        }

        String url = resolveMappingsUrl(mcVersion, server);
        if (url == null) {
            AgentLogger.getInstance().warn(
                "No " + side + "_mappings URL found for version " + mcVersion);
            return null;
        }

        AgentLogger.getInstance().info("Downloading " + side + " mappings for "
            + mcVersion + " …");
        Path tmp = Files.createTempFile("bcdebug-mappings-", ".txt");
        try {
            httpDownload(url, tmp);
            RuntimeMappings mappings = RuntimeMappings.load(tmp);
            Files.move(tmp, cached, StandardCopyOption.REPLACE_EXISTING);
            AgentLogger.getInstance().info("Cached mappings: " + cached.getFileName()
                + " (" + mappings.size() + " classes)");
            return mappings;
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** Look up the mappings download URL via the version manifest. */
    static String resolveMappingsUrl(String mcVersion, boolean server) throws IOException {
        Path manifest = downloadToTemp(VERSION_MANIFEST);
        try {
            String json = Files.readString(manifest, StandardCharsets.UTF_8);
            // Minimal extraction without pulling in Gson into bc-agent-core:
            // locate "id":"<mcVersion>" then the following versions[].url.
            String versionsUrl = findVersionsEntryUrl(json, mcVersion);
            if (versionsUrl == null) return null;

            Path versionJson = downloadToTemp(versionsUrl);
            try {
                String vjson = Files.readString(versionJson, StandardCharsets.UTF_8);
                return extractDownloadsUrl(vjson, server ? "server_mappings" : "client_mappings");
            } finally {
                Files.deleteIfExists(versionJson);
            }
        } finally {
            Files.deleteIfExists(manifest);
        }
    }

    /** Look up the mappings download URL via the version manifest JSON. */
    public static String findVersionsEntryUrl(String manifestJson, String versionId) {
        String needle = "\"id\":\"" + versionId + "\"";
        int idx = manifestJson.indexOf(needle);
        if (idx < 0) {
            // tolerate spaced JSON
            needle = "\"id\": \"" + versionId + "\"";
            idx = manifestJson.indexOf(needle);
            if (idx < 0) return null;
        }
        return jsonStringValueAfter(manifestJson, "\"url\"", idx);
    }

    /** Extract the string value of {@code key} occurring after {@code from}. */
    static String jsonStringValueAfter(String json, String key, int from) {
        int keyIdx = json.indexOf(key, from);
        if (keyIdx < 0) return null;
        int colon = json.indexOf(':', keyIdx + key.length());
        if (colon < 0) return null;
        int open = json.indexOf('"', colon);
        if (open < 0) return null;
        int close = json.indexOf('"', open + 1);
        if (close < 0) return null;
        return json.substring(open + 1, close);
    }

    static String extractDownloadsUrl(String versionJson, String key) {
        int idx = versionJson.indexOf("\"" + key + "\":");
        if (idx < 0) return null;
        // Sanity window: the mappings object is small; don't match a "url"
        // belonging to some later section.
        int sectionEnd = versionJson.indexOf('}', idx);
        String value = jsonStringValueAfter(versionJson, "\"url\"", idx);
        if (value == null) return null;
        int valuePos = versionJson.indexOf(value, idx);
        if (sectionEnd >= 0 && valuePos > sectionEnd) return null;
        return value;
    }

    private static Path downloadToTemp(String url) throws IOException {
        Path tmp = Files.createTempFile("bcdebug-http-", ".tmp");
        httpDownload(url, tmp);
        return tmp;
    }

    private static void httpDownload(String url, Path target) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(READ_TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", "BCDebugJavaAgent");
        int code = conn.getResponseCode();
        if (code != 200) {
            conn.disconnect();
            throw new IOException("HTTP " + code + " for " + url);
        }
        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            conn.disconnect();
        }
    }
}
