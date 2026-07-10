package com.protean.copilot.dependency;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DependencyManagerTest {

    @Test
    public void compareVersionsHandlesDifferentPatchLevels() {
        assertEquals(0, DependencyManager.compareVersions("0.143.0", "0.143.0"));
        assertEquals(1, DependencyManager.compareVersions("0.143.0", "0.142.5"));
        assertEquals(-1, DependencyManager.compareVersions("0.142.4", "0.143.0"));
    }

    @Test
    public void codexDefinitionUsesInstallableLockedVersion() {
        assertEquals("0.143.0", SdkDefinition.CODEX_SDK.getLockedVersion());
        assertEquals("0.143.0", SdkDefinition.CODEX_SDK.getRequestedVersion(null));
        assertEquals("@openai/codex-sdk@0.143.0", SdkDefinition.CODEX_SDK.getAllPackages(null).get(0));
    }

    @Test
    public void claudeDefinitionUsesTheAgentSdkConsumedByTheRuntimeBridge() {
        assertEquals("@anthropic-ai/claude-agent-sdk", SdkDefinition.CLAUDE_SDK.getNpmPackage());
        assertEquals("0.2.58", SdkDefinition.CLAUDE_SDK.getLockedVersion());
    }
}
