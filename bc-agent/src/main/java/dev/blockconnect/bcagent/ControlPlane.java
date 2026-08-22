package dev.blockconnect.bcagent;

import java.io.IOException;

/**
 * Minimal control-plane contract shared by the JDK HttpServer frontend and
 * the raw ServerSocket fallback.
 */
public interface ControlPlane {

    /** Bind and start serving requests. */
    void start() throws IOException;

    /** Stop serving and release the port. */
    void stop();
}
