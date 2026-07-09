package com.protean.copilot.provider.codex;

import com.protean.copilot.cache.SessionIndexEntry;
import com.protean.copilot.history.ProviderHistorySource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class CodexHistorySource implements ProviderHistorySource {

    private final CodexHistoryReader historyReader;

    public CodexHistorySource() {
        this(new CodexHistoryReader());
    }

    CodexHistorySource(@NotNull CodexHistoryReader historyReader) {
        this.historyReader = historyReader;
    }

    @Override
    public @NotNull String providerId() {
        return "codex";
    }

    @Override
    public @NotNull List<SessionIndexEntry> listEntries(@Nullable String projectPath, boolean forceRefresh) {
        if (projectPath == null || projectPath.isBlank()) {
            return List.of();
        }

        List<SessionIndexEntry> entries = new ArrayList<>();
        for (CodexHistoryReader.SessionInfo session : historyReader.readProjectSessions(projectPath, forceRefresh)) {
            if (session.sessionId == null || session.sessionId.isBlank()) {
                continue;
            }
            entries.add(new SessionIndexEntry(
                session.sessionId,
                session.title == null || session.title.isBlank() ? "Untitled session" : session.title,
                providerId(),
                session.cwd == null || session.cwd.isBlank() ? projectPath : session.cwd,
                session.lastTimestamp,
                Math.max(0, session.messageCount),
                Math.max(0L, session.fileSize),
                false,
                0L,
                null,
                session.entrypoint == null || session.entrypoint.isBlank() ? "codex-cli" : session.entrypoint
            ));
        }
        return entries;
    }
}
