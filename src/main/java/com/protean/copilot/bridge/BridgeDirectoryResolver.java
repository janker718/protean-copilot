package com.protean.copilot.bridge;

import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves and prepares the runtime bridge directory.
 */
public final class BridgeDirectoryResolver {

    private static final Logger LOG = Logger.getInstance(BridgeDirectoryResolver.class);

    private final Path bridgeRoot;

    public BridgeDirectoryResolver() {
        this(BridgePathLocator.resolveBridgeRoot());
    }

    BridgeDirectoryResolver(Path bridgeRoot) {
        this.bridgeRoot = bridgeRoot;
    }

    public Path getBridgeRoot() {
        return bridgeRoot;
    }

    public Path resolveBridgeScript(String providerName, String resourcePath, ClassLoader classLoader) throws IOException {
        Path providerDir = bridgeRoot.resolve(providerName == null || providerName.isBlank()
            ? "shared"
            : providerName.trim().toLowerCase());
        Files.createDirectories(providerDir);
        Path script = BridgeArchiveExtractor.extractResource(classLoader, resourcePath, providerDir);
        LOG.info("Resolved bridge script: provider=" + providerName + ", path=" + script.toAbsolutePath());
        return script;
    }
}
