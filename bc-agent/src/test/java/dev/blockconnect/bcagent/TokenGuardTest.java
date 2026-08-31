package dev.blockconnect.bcagent;

import dev.blockconnect.bcagent.core.AgentConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenGuardTest {

    private AgentConfig configWithToken(String token) {
        AgentConfig config = AgentConfig.parse("httptoken=" + token);
        return config;
    }

    @Test
    void openWhenNoTokenConfigured() {
        AgentConfig config = AgentConfig.parse(null);
        assertFalse(TokenGuard.isEnabled(config));
        assertTrue(TokenGuard.authorized(config, null));
        assertTrue(TokenGuard.authorized(config, "anything"));
        assertTrue(TokenGuard.authorized(config, ""));
    }

    @Test
    void tokenRequiredWhenConfigured() {
        AgentConfig config = configWithToken("s3cret");
        assertTrue(TokenGuard.isEnabled(config));
        assertFalse(TokenGuard.authorized(config, null));
        assertFalse(TokenGuard.authorized(config, ""));
        assertFalse(TokenGuard.authorized(config, "wrong"));
        assertFalse(TokenGuard.authorized(config, "s3cretX"));
        assertTrue(TokenGuard.authorized(config, "s3cret"));
    }

    @Test
    void configValueIsTrimmed() {
        AgentConfig config = AgentConfig.parse("httptoken= spaced ");
        assertTrue(TokenGuard.isEnabled(config));
        assertTrue(TokenGuard.authorized(config, "spaced"));
        assertFalse(TokenGuard.authorized(config, " spaced "));
    }
}
