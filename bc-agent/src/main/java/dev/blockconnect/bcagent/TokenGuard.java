package dev.blockconnect.bcagent;

import dev.blockconnect.bcagent.core.AgentConfig;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Control-plane authorization.
 * <p>
 * When {@code httpToken} is set in the agent config, every endpoint requires
 * the token — presented either as the {@code X-BCDebug-Token} header or the
 * {@code token} query parameter. With no token configured, the control plane
 * keeps its loopback-only default posture.
 * <p>
 * Comparison is constant-time via {@link MessageDigest#isEqual}.
 */
public final class TokenGuard {

    public static final String HEADER = "X-BCDebug-Token";
    public static final String QUERY_PARAM = "token";

    private TokenGuard() {}

    /** True when the control plane is open (no token configured). */
    public static boolean isEnabled(AgentConfig config) {
        return config != null
            && config.httpToken != null
            && !config.httpToken.trim().isEmpty();
    }

    /** Constant-time comparison of the presented token against the config. */
    public static boolean authorized(AgentConfig config, String presented) {
        if (!isEnabled(config)) return true;
        if (presented == null) return false;
        byte[] expected = config.httpToken.trim().getBytes(StandardCharsets.UTF_8);
        byte[] got = presented.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, got);
    }
}
