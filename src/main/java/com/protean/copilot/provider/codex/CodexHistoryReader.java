package com.protean.copilot.provider.codex;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class CodexHistoryReader {

    private static final Logger LOG = Logger.getInstance(CodexHistoryReader.class);

    private final Gson gson;
    private final CodexHistoryParser parser;
    private final CodexHistoryIndexService indexService;

    public CodexHistoryReader() {
        this(defaultSessionsDir(), new Gson());
    }

    CodexHistoryReader(@NotNull Path sessionsDir, @NotNull Gson gson) {
        this.gson = gson;
        this.parser = new CodexHistoryParser(gson);
        this.indexService = new CodexHistoryIndexService(sessionsDir, parser);
    }

    public static final class SessionInfo {
        public String sessionId;
        public String title;
        public int messageCount;
        public long lastTimestamp;
        public long firstTimestamp;
        public String cwd;
        public long fileSize;
        public String entrypoint;

        long lastTimestamp() {
            return lastTimestamp;
        }
    }

    public @NotNull List<SessionInfo> readProjectSessions(@Nullable String projectPath, boolean forceRefresh) {
        return indexService.readProjectSessions(projectPath, forceRefresh);
    }

    public @NotNull List<JsonObject> readSessionMessages(@NotNull String sessionId, @Nullable String cwd) {
        Path sessionFile = indexService.findSessionFile(sessionId, cwd);
        if (sessionFile == null) {
            return List.of();
        }
        try {
            return parser.parseSessionMessages(sessionFile);
        } catch (Exception ex) {
            LOG.warn("Failed to read Codex session messages: " + ex.getMessage(), ex);
            return List.of();
        }
    }

    public @NotNull String getSessionMessagesAsJson(@NotNull String sessionId, @Nullable String cwd) {
        return gson.toJson(readSessionMessages(sessionId, cwd));
    }

    private static @NotNull Path defaultSessionsDir() {
        return Paths.get(System.getProperty("user.home"), ".codex", "sessions");
    }
}
