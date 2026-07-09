package com.protean.copilot.handler.history;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.history.HistoryIndexService;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Deletes history entries from the aggregate index and provider-owned storage.
 */
public final class HistoryDeleteService {

    private static final Logger LOG = Logger.getInstance(HistoryDeleteService.class);

    private final HandlerContext context;

    public HistoryDeleteService(@NotNull HandlerContext context) {
        this.context = context;
    }

    public void handleDeleteSession(String content, String preferredProvider) {
        String sessionId = normalize(content);
        if (sessionId == null) {
            return;
        }
        deleteSingleSession(sessionId, preferredProvider);
    }

    public void handleDeleteSessions(String content, String preferredProvider) {
        try {
            JsonArray ids = JsonParser.parseString(content).getAsJsonArray();
            for (int i = 0; i < ids.size(); i++) {
                if (ids.get(i).isJsonNull()) {
                    continue;
                }
                String sessionId = normalize(ids.get(i).getAsString());
                if (sessionId != null) {
                    deleteSingleSession(sessionId, preferredProvider);
                }
            }
        } catch (Exception ex) {
            LOG.warn("Failed to delete history sessions: " + ex.getMessage(), ex);
        }
    }

    private void deleteSingleSession(@NotNull String sessionId, String preferredProvider) {
        var entry = HistoryIndexService.getInstance(context.project)
            .getEntry(sessionId, context.project.getBasePath(), preferredProvider);
        String provider = entry != null && entry.provider() != null ? entry.provider() : preferredProvider;

        if ("codex".equalsIgnoreCase(provider)) {
            deleteCodexSessionFiles(sessionId);
        }

        HistoryIndexService.getInstance(context.project).remove(sessionId);
    }

    private void deleteCodexSessionFiles(@NotNull String sessionId) {
        Path sessionsDir = Path.of(System.getProperty("user.home"), ".codex", "sessions");
        if (!Files.isDirectory(sessionsDir)) {
            return;
        }

        List<Path> matches = new ArrayList<>();
        try (var paths = Files.walk(sessionsDir)) {
            paths.filter(Files::isRegularFile)
                .filter(path -> isCodexSessionFileMatch(path, sessionId))
                .forEach(matches::add);
        } catch (IOException ex) {
            LOG.warn("Failed to scan Codex history for deletion: " + ex.getMessage(), ex);
            return;
        }

        for (Path match : matches) {
            try {
                Files.deleteIfExists(match);
            } catch (IOException ex) {
                LOG.warn("Failed to delete Codex history file " + match + ": " + ex.getMessage(), ex);
            }
        }
    }

    static boolean isCodexSessionFileMatch(@NotNull Path path, @NotNull String sessionId) {
        String fileName = path.getFileName().toString();
        return fileName.equals(sessionId + ".jsonl") || fileName.endsWith("-" + sessionId + ".jsonl");
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
