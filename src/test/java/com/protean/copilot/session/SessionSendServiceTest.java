package com.protean.copilot.session;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SessionSendServiceTest {

    @Test
    public void keepsCodexPlanModeForReadOnlySandboxRuns() {
        assertEquals("plan", SessionSendService.resolveEffectivePermissionMode("codex", "plan", null));
    }

    @Test
    public void keepsClaudePlanModeUntouched() {
        assertEquals("plan", SessionSendService.resolveEffectivePermissionMode("claude", "plan", null));
    }

    @Test
    public void fallsBackToDefaultPermissionModeWhenNothingRequested() {
        assertEquals("default", SessionSendService.resolveEffectivePermissionMode("claude", null, null));
    }

    @Test
    public void usesSessionDefaultPermissionModeWhenRequestDoesNotOverride() {
        assertEquals("default", SessionSendService.resolveEffectivePermissionMode("claude", null, "default"));
    }

    @Test
    public void codexRuntimeAccessErrorRequiresAuthorizationOrManagedProvider() {
        assertEquals(
            "Codex local configuration access is not authorized. Please authorize local ~/.codex access or enable a managed Codex provider first.",
            SessionSendService.getCodexRuntimeAccessError("inactive")
        );
        assertNull(SessionSendService.getCodexRuntimeAccessError("managed"));
        assertNull(SessionSendService.getCodexRuntimeAccessError("cli_login"));
    }
}
