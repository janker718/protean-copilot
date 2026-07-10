package com.protean.copilot.session;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SessionMessageOrchestratorTest {

    @Test
    public void loadFromServerStoresFriendlyResumeFailure() throws Exception {
        ChatSession session = createSession("codex", "thread-1", "/workspace");

        SessionMessageOrchestrator orchestrator = new SessionMessageOrchestrator(
            session,
            new MessageParser(),
            new SessionMessageOrchestrator.SessionHistoryAccess() {
                @Override
                public List<JsonObject> getProviderSessionMessages(String provider, String sessionId, String cwd) {
                    throw new RuntimeException("thread/resume returned 404");
                }
            },
            () -> null
        );

        try {
            orchestrator.loadFromServer().join();
        } catch (Exception ignored) {
        }

        assertEquals(
            "Codex session resume failed. thread/resume returned 404.",
            session.getError()
        );
        assertTrue(session.getMessages().isEmpty());
        assertFalse(session.requiresProviderResume());
    }

    @Test
    public void successfulCodexHistoryLoadMarksSessionForRuntimeResume() throws Exception {
        ChatSession session = createSession("codex", "thread-1", "/workspace");
        SessionMessageOrchestrator orchestrator = new SessionMessageOrchestrator(
            session,
            new MessageParser(),
            (provider, sessionId, cwd) -> List.of(),
            () -> null
        );

        orchestrator.loadFromServer().join();

        assertTrue(session.requiresProviderResume());
    }

    @Test
    public void ingestProviderMessagesJsonPreservesPendingUserMessageWhenSnapshotLags() throws Exception {
        ChatSession session = createSession("codex", "thread-1", "/workspace");
        ChatSession.Message previousAssistant = new ChatSession.Message(ChatSession.Message.Type.ASSISTANT, "older reply");
        previousAssistant.timestamp = 1_000L;
        ChatSession.Message pendingUser = new ChatSession.Message(ChatSession.Message.Type.USER, "latest question");
        pendingUser.timestamp = 2_000L;
        session.replaceMessages(List.of(previousAssistant, pendingUser));

        RecordingCallback callback = new RecordingCallback();
        SessionMessageOrchestrator orchestrator = new SessionMessageOrchestrator(
            session,
            new MessageParser(),
            (provider, sessionId, cwd) -> List.of(),
            () -> callback
        );

        orchestrator.ingestProviderMessagesJson("""
            [
              {"type":"assistant","message":{"content":[{"type":"text","text":"older reply"}]},"timestamp":"1970-01-01T00:00:01Z"}
            ]
            """);

        List<ChatSession.Message> messages = session.getMessages();
        assertEquals(2, messages.size());
        assertEquals(ChatSession.Message.Type.USER, messages.get(1).type);
        assertEquals("latest question", messages.get(1).content);

        JsonArray pushed = JsonParser.parseString(callback.lastMessagesJson).getAsJsonArray();
        assertEquals(2, pushed.size());
        assertEquals("user", pushed.get(1).getAsJsonObject().get("type").getAsString());
        assertEquals("latest question", pushed.get(1).getAsJsonObject().get("content").getAsString());
    }

    @Test
    public void ingestProviderMessagesJsonDoesNotDuplicatePendingUserMessageWhenSnapshotCatchesUp() throws Exception {
        ChatSession session = createSession("codex", "thread-1", "/workspace");
        ChatSession.Message pendingUser = new ChatSession.Message(ChatSession.Message.Type.USER, "latest question");
        pendingUser.timestamp = 2_000L;
        session.replaceMessages(List.of(pendingUser));

        SessionMessageOrchestrator orchestrator = new SessionMessageOrchestrator(
            session,
            new MessageParser(),
            (provider, sessionId, cwd) -> List.of(),
            () -> null
        );

        orchestrator.ingestProviderMessagesJson("""
            [
              {"type":"user","message":{"content":[{"type":"text","text":"latest question"}]},"timestamp":"1970-01-01T00:00:02Z"}
            ]
            """);

        List<ChatSession.Message> messages = session.getMessages();
        assertEquals(1, messages.size());
        assertEquals(ChatSession.Message.Type.USER, messages.get(0).type);
        assertEquals("latest question", messages.get(0).content);
    }

    @Test
    public void updateSessionIdRotatesRuntimeEpochForCodexRemap() throws Exception {
        ChatSession session = createSession("codex", "request-1", "/workspace");
        session.setRuntimeSessionEpoch("epoch-1");

        SessionMessageOrchestrator orchestrator = new SessionMessageOrchestrator(
            session,
            new MessageParser(),
            (provider, sessionId, cwd) -> List.of(),
            () -> null
        );

        assertTrue(orchestrator.handleBridgeEvent("updateSessionId", "thread-42", "request-1"));

        assertEquals("thread-42", session.getSessionId());
        assertFalse("epoch-1".equals(session.getRuntimeSessionEpoch()));
    }

    private static ChatSession createSession(String provider, String sessionId, String cwd) throws Exception {
        ChatSession session = (ChatSession) getUnsafe().allocateInstance(ChatSession.class);
        setField(session, "messages", new ArrayList<ChatSession.Message>());
        setField(session, "provider", provider);
        setField(session, "sessionId", sessionId);
        setField(session, "cwd", cwd);
        setField(session, "lastModifiedTime", System.currentTimeMillis());
        return session;
    }

    private static void setField(ChatSession session, String fieldName, Object value) throws Exception {
        Field field = ChatSession.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(session, value);
    }

    private static sun.misc.Unsafe getUnsafe() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
    }

    private static final class RecordingCallback extends SessionCallbackAdapter {
        private String lastMessagesJson;

        private RecordingCallback() {
            super(new StreamMessageCoalescer(new StreamMessageCoalescer.JsCallbackTarget() {
                    @Override
                    public void callJavaScript(String functionName, String... args) {
                    }

                    @Override
                    public com.intellij.ui.jcef.JBCefBrowser getBrowser() {
                        return null;
                    }

                    @Override
                    public boolean isDisposed() {
                        return false;
                    }

                    @Override
                    public com.protean.copilot.handler.core.HandlerContext getHandlerContext() {
                        return null;
                    }
                }),
                (functionName, args) -> { },
                null,
                () -> false,
                () -> { });
        }

        @Override
        public void updateMessages(String messagesJson) {
            this.lastMessagesJson = messagesJson;
        }
    }
}
