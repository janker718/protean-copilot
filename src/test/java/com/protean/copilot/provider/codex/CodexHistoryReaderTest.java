package com.protean.copilot.provider.codex;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class CodexHistoryReaderTest {

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void readsProjectSessionsAndRestorableMessages() throws Exception {
        Path sessionsDir = tmp.newFolder("sessions").toPath();
        Path projectDir = Path.of("/tmp/protean-project");
        Path sessionDir = Files.createDirectories(sessionsDir.resolve("2026").resolve("07").resolve("09"));
        Path sessionFile = sessionDir.resolve("rollout-session-123.jsonl");

        Files.writeString(sessionFile, """
            {"type":"session_meta","timestamp":"2026-07-09T10:00:00Z","payload":{"id":"session-123","cwd":"/tmp/protean-project","timestamp":"2026-07-09T10:00:00Z"}}
            {"type":"event_msg","timestamp":"2026-07-09T10:00:01Z","payload":{"type":"user_message","message":"<agents-instructions>hidden</agents-instructions>Fix bug"}}
            {"type":"response_item","timestamp":"2026-07-09T10:00:02Z","payload":{"type":"agent_message","text":"Patched successfully"}}
            {"type":"response_item","timestamp":"2026-07-09T10:00:03Z","payload":{"type":"function_call","call_id":"call-1","name":"write","arguments":{"file_path":"README.md"}}}
            {"type":"response_item","timestamp":"2026-07-09T10:00:04Z","payload":{"type":"function_call_output","call_id":"call-1","output":"done","status":"completed"}}
            """);

        CodexHistoryReader reader = new CodexHistoryReader(sessionsDir, new Gson());

        List<CodexHistoryReader.SessionInfo> sessions = reader.readProjectSessions(projectDir.toString(), false);
        assertEquals(1, sessions.size());
        assertEquals("session-123", sessions.get(0).sessionId);
        assertEquals("Fix bug", sessions.get(0).title);
        assertEquals(4, sessions.get(0).messageCount);

        List<JsonObject> messages = reader.readSessionMessages("session-123", projectDir.toString());
        assertEquals(4, messages.size());
        assertEquals("user", messages.get(0).get("type").getAsString());
        assertEquals("assistant", messages.get(1).get("type").getAsString());
        assertEquals("tool_use", messages.get(2).getAsJsonObject("message")
            .getAsJsonArray("content").get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("tool_result", messages.get(3).getAsJsonObject("message")
            .getAsJsonArray("content").get(0).getAsJsonObject().get("type").getAsString());
        assertFalse(reader.getSessionMessagesAsJson("session-123", projectDir.toString()).isBlank());
    }
}
