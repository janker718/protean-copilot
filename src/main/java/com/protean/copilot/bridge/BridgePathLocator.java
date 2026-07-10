package com.protean.copilot.bridge;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Locates the runtime directory used to materialize bridge resources.
 *
 * <p>The current project ships provider-specific bridge scripts as classpath resources
 * rather than a packaged ai-bridge archive. This helper aligns the directory-discovery
 * shape with the reference project while adapting it to the current runtime layout.</p>
 */
public final class BridgePathLocator {

    static final String BRIDGE_ROOT_PROPERTY = "protean.bridge.dir";
    static final String BRIDGE_ROOT_ENV = "PROTEAN_BRIDGE_DIR";
    static final String DEFAULT_BRIDGE_DIR_NAME = "bridge-runtime";

    private BridgePathLocator() {
    }

    public static Path resolveBridgeRoot() {
        Path configured = configuredPath(System.getProperty(BRIDGE_ROOT_PROPERTY));
        if (configured != null) {
            return configured;
        }
        configured = configuredPath(System.getenv(BRIDGE_ROOT_ENV));
        if (configured != null) {
            return configured;
        }
        return Path.of(System.getProperty("user.home"), ".codemoss", DEFAULT_BRIDGE_DIR_NAME);
    }

    private static Path configuredPath(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        Path path = Path.of(raw.trim()).toAbsolutePath().normalize();
        return Files.isDirectory(path) || !Files.exists(path) ? path : null;
    }

    public static String fileNameForResource(String resourcePath) {
        Path path = Path.of(resourcePath.replace('\\', '/'));
        return path.getFileName().toString();
    }
}
