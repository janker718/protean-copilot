package com.protean.copilot.bridge;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BridgeDirectoryResolverTest {

    @Test
    public void resolvesBridgeScriptIntoProviderScopedRuntimeDirectory() throws Exception {
        Path root = Files.createTempDirectory("bridge-runtime-");
        BridgeDirectoryResolver resolver = new BridgeDirectoryResolver(root);

        Path script = resolver.resolveBridgeScript("claude", "bridge/claude-sdk-bridge.mjs", getClass().getClassLoader());

        assertTrue(Files.exists(script));
        assertEquals(root.resolve("claude").resolve("claude-sdk-bridge.mjs"), script);
    }
}
