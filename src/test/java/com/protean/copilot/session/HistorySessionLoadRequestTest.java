package com.protean.copilot.session;

import com.protean.copilot.settings.TabStateService;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class HistorySessionLoadRequestTest {

    @Test
    public void normalizesBlankProviderToClaude() {
        HistorySessionLoadRequest request = HistorySessionLoadRequest.of(" session-1 ", " /tmp/project ", "   ");

        assertEquals("session-1", request.sessionId());
        assertEquals("/tmp/project", request.projectPath());
        assertEquals("claude", request.provider());
    }

    @Test
    public void createsRequestFromTabState() {
        TabStateService.TabSessionState state = new TabStateService.TabSessionState(
            "claude",
            " session-2 ",
            " /tmp/history ",
            "default",
            "bypassPermissions",
            null
        );

        HistorySessionLoadRequest request = HistorySessionLoadRequest.fromTabState(state);

        assertEquals("session-2", request.sessionId());
        assertEquals("/tmp/history", request.projectPath());
        assertEquals("claude", request.provider());
    }

    @Test
    public void ignoresTabStateWithoutSessionId() {
        TabStateService.TabSessionState state = new TabStateService.TabSessionState(
            "claude",
            "   ",
            "/tmp/history",
            "default",
            "bypassPermissions",
            null
        );

        assertNull(HistorySessionLoadRequest.fromTabState(state));
    }
}
