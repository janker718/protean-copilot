package com.protean.copilot.session;

import com.google.gson.JsonObject;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
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
}
