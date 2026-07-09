package com.protean.copilot.provider.claude;

import com.protean.copilot.cache.SessionIndexEntry;
import com.protean.copilot.history.ProviderHistorySource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Claude-backed provider history source used by the aggregate history index.
 */
public final class ClaudeHistorySource implements ProviderHistorySource {

    private final ClaudeHistoryReader historyReader;

    public ClaudeHistorySource() {
        this(new ClaudeHistoryReader());
    }

    ClaudeHistorySource(@NotNull ClaudeHistoryReader historyReader) {
        this.historyReader = historyReader;
    }

    @Override
    public @NotNull String providerId() {
        return "claude";
    }

    @Override
    public @NotNull List<SessionIndexEntry> listEntries(@Nullable String projectPath, boolean forceRefresh) {
        if (projectPath == null || projectPath.isBlank()) {
            return List.of();
        }

        List<SessionIndexEntry> entries = new ArrayList<>();
        for (ClaudeHistoryReader.SessionInfo session : historyReader.readProjectSessions(projectPath, forceRefresh)) {
            if (session == null || session.sessionId == null || session.sessionId.isBlank()) {
                continue;
            }
            String summary = session.title == null || session.title.isBlank()
                ? "Untitled session"
                : session.title;
            String entrypoint = session.entrypoint == null || session.entrypoint.isBlank()
                ? "sdk-cli"
                : session.entrypoint;
            entries.add(new SessionIndexEntry(
                session.sessionId,
                summary,
                providerId(),
                projectPath,
                session.lastTimestamp,
                Math.max(session.messageCount, 0),
                Math.max(session.fileSize, 0L),
                false,
                0L,
                null,
                entrypoint
            ));
        }
        return entries;
    }
}
