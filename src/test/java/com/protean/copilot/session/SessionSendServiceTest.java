package com.protean.copilot.session;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SessionSendServiceTest {

    @Test
    public void keepsCodexPlanModeForReadOnlySandboxRuns() {
        assertEquals("plan", SessionSendService.resolveEffectivePermissionMode("codex", "plan", null));
    }

    @Test
    public void keepsClaudePlanModeUntouched() {
        assertEquals("plan", SessionSendService.resolveEffectivePermissionMode("claude", "plan", null));
    }
}
