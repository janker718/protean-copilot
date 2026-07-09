package com.protean.copilot.session;

import com.google.gson.JsonObject;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SessionProviderRouterTest {

    @Test
    public void routesBlankProviderToClaudeAdapter() {
        RecordingAdapter claude = new RecordingAdapter("claude");
        SessionProviderRouter router = new SessionProviderRouter(List.of(claude));

        router.launchChannel(null, "channel-1", "session-1", "/tmp/project").join();

        assertEquals("launch:channel-1:session-1:/tmp/project", claude.events.get(0));
    }

    @Test
    public void routesHistoryReadsToMatchingProvider() {
        RecordingAdapter claude = new RecordingAdapter("claude");
        RecordingAdapter codex = new RecordingAdapter("codex");
        SessionProviderRouter router = new SessionProviderRouter(List.of(claude, codex));

        List<JsonObject> messages = router.getSessionMessages("codex", "session-2", "/tmp/codex");

        assertEquals(1, messages.size());
        assertEquals("codex", messages.get(0).get("provider").getAsString());
        assertEquals("history:session-2:/tmp/codex", codex.events.get(0));
        assertTrue(claude.events.isEmpty());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnknownProvider() {
        SessionProviderRouter router = new SessionProviderRouter(List.of(new RecordingAdapter("claude")));
        router.getSessionMessages("unknown", "session-3", "/tmp/project");
    }

    private static final class RecordingAdapter implements SessionProviderAdapter {

        private final String providerId;
        private final List<String> events = new java.util.ArrayList<>();

        private RecordingAdapter(String providerId) {
            this.providerId = providerId;
        }

        @Override
        public String providerId() {
            return providerId;
        }

        @Override
        public CompletableFuture<String> launchChannel(String channelId, String sessionId, String cwd) {
            events.add("launch:" + channelId + ":" + sessionId + ":" + cwd);
            return CompletableFuture.completedFuture(channelId);
        }

        @Override
        public CompletableFuture<Void> interruptChannel(String channelId, String sessionId) {
            events.add("interrupt:" + channelId + ":" + sessionId);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public List<JsonObject> getSessionMessages(String sessionId, String cwd) {
            events.add("history:" + sessionId + ":" + cwd);
            JsonObject payload = new JsonObject();
            payload.addProperty("provider", providerId);
            payload.addProperty("sessionId", sessionId);
            return List.of(payload);
        }
    }
}
