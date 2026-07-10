package com.protean.copilot.bridge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Materializes bridge resources into a runtime directory.
 *
 * <p>We intentionally keep the reference project's extractor-oriented naming so the
 * surrounding code structure stays familiar, even though the current repository does
 * not yet ship a bundled ai-bridge archive.</p>
 */
final class BridgeArchiveExtractor {

    private BridgeArchiveExtractor() {
    }

    static Path extractResource(ClassLoader classLoader, String resourcePath, Path targetDir) throws IOException {
        InputStream input = classLoader.getResourceAsStream(resourcePath);
        if (input == null) {
            throw new IOException("Bridge resource not found: " + resourcePath);
        }
        Files.createDirectories(targetDir);
        Path target = targetDir.resolve(BridgePathLocator.fileNameForResource(resourcePath));
        try (input) {
            byte[] bytes = input.readAllBytes();
            String content = new String(bytes, StandardCharsets.UTF_8);
            Files.writeString(
                target,
                content,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
            );
        }
        return target;
    }
}
