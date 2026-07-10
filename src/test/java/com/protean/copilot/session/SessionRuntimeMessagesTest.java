package com.protean.copilot.session;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SessionRuntimeMessagesTest {

    @Test
    public void classifiesPermissionDeniedErrors() {
        String message = SessionRuntimeMessages.bridgeError(
            "codex",
            "PERMISSION_DENIED",
            "approval denied for apply_patch",
            "query",
            "Retry after requesting approval"
        );

        assertEquals(
            "Codex permission request was denied. approval denied for apply_patch. Retry after requesting approval.",
            message
        );
    }

    @Test
    public void classifiesResumeFailures() {
        String message = SessionRuntimeMessages.historyResumeFailed(
            "codex",
            "thread/resume returned 404"
        );

        assertEquals(
            "Codex session resume failed. thread/resume returned 404.",
            message
        );
    }

    @Test
    public void buildsStableUnavailableMessage() {
        String message = SessionRuntimeMessages.sdkUnavailable(
            "codex",
            "0.143.0",
            "Install the locked Codex SDK before retrying",
            null
        );

        assertTrue(message.contains("Codex runtime unavailable"));
        assertTrue(message.contains("version 0.143.0"));
        assertTrue(message.contains("Install the locked Codex SDK before retrying."));
    }

    @Test
    public void normalizesDependencyInstallPermissionDenied() {
        String message = SessionRuntimeMessages.dependencyInstallFailed(
            "Codex SDK",
            "approval denied for apply_patch"
        );

        assertEquals(
            "Codex SDK permission request was denied. approval denied for apply_patch.",
            message
        );
    }

    @Test
    public void normalizesDependencyStatusFailure() {
        String message = SessionRuntimeMessages.dependencyStatusUnavailable(
            new IllegalStateException("status backend exploded")
        );

        assertEquals("Dependency status unavailable. status backend exploded.", message);
    }
}
