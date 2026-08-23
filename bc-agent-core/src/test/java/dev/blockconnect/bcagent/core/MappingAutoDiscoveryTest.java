package dev.blockconnect.bcagent.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MappingAutoDiscoveryTest {

    @TempDir
    Path tempDir;

    private static final String MANIFEST_JSON = "{\"latest\":{\"release\":\"1.21.1\"},"
        + "\"versions\":[{" 
        + "\"id\":\"1.21.1\",\"type\":\"release\","
        + "\"url\":\"https://example.invalid/version/1.21.1.json\"}]}";

    private static final String VERSION_JSON = "{\"id\":\"1.21.1\","
        + "\"downloads\":{"
        + "\"server\":{\"sha1\":\"x\",\"size\":1,\"url\":\"https://example.invalid/server.jar\"},"
        + "\"server_mappings\":{\"sha1\":\"y\",\"size\":2,"
        + "\"url\":\"https://example.invalid/server.txt\"},"
        + "\"client_mappings\":{\"sha1\":\"z\",\"size\":3,"
        + "\"url\":\"https://example.invalid/client.txt\"}}}";

    @Test
    void extractsVersionsEntryUrl() {
        String url = MappingAutoDiscovery.findVersionsEntryUrl(MANIFEST_JSON, "1.21.1");
        assertEquals("https://example.invalid/version/1.21.1.json", url);
    }

    @Test
    void extractsServerMappingsUrlFromVersionJson() {
        String url = MappingAutoDiscovery.extractDownloadsUrl(VERSION_JSON, "server_mappings");
        assertEquals("https://example.invalid/server.txt", url);
    }

    @Test
    void extractsClientMappingsUrlFromVersionJson() {
        String url = MappingAutoDiscovery.extractDownloadsUrl(VERSION_JSON, "client_mappings");
        assertEquals("https://example.invalid/client.txt", url);
    }

    @Test
    void returnsNullForMissingKey() {
        assertNull(MappingAutoDiscovery.extractDownloadsUrl(VERSION_JSON, "bogus_mappings"));
    }

    @Test
    void returnsNullForUnknownVersion() {
        assertNull(MappingAutoDiscovery.findVersionsEntryUrl(MANIFEST_JSON, "9.9.9"));
    }
}
