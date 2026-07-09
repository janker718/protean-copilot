package com.protean.copilot.handler.history;

import org.junit.Test;

import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HistoryDeleteServiceTest {

    @Test
    public void matchesCodexSessionFilesBySuffix() {
        assertTrue(HistoryDeleteService.isCodexSessionFileMatch(
            Path.of("/tmp/.codex/sessions/2026/07/09/rollout-session-123.jsonl"),
            "session-123"
        ));
        assertTrue(HistoryDeleteService.isCodexSessionFileMatch(
            Path.of("/tmp/.codex/sessions/session-123.jsonl"),
            "session-123"
        ));
        assertFalse(HistoryDeleteService.isCodexSessionFileMatch(
            Path.of("/tmp/.codex/sessions/rollout-other.jsonl"),
            "session-123"
        ));
    }
}
