package com.protean.copilot.handler.history;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.history.HistoryIndexService;
import org.jetbrains.annotations.NotNull;

/**
 * Deletes history entries from the in-memory session index.
 */
public final class HistoryDeleteService {

    private static final Logger LOG = Logger.getInstance(HistoryDeleteService.class);

    private final HandlerContext context;

    public HistoryDeleteService(@NotNull HandlerContext context) {
        this.context = context;
    }

    public void handleDeleteSession(String content) {
        String sessionId = normalize(content);
        if (sessionId == null) {
            return;
        }
        HistoryIndexService.getInstance(context.project).remove(sessionId);
    }

    public void handleDeleteSessions(String content) {
        try {
            JsonArray ids = JsonParser.parseString(content).getAsJsonArray();
            for (int i = 0; i < ids.size(); i++) {
                if (ids.get(i).isJsonNull()) {
                    continue;
                }
                String sessionId = normalize(ids.get(i).getAsString());
                if (sessionId != null) {
                    HistoryIndexService.getInstance(context.project).remove(sessionId);
                }
            }
        } catch (Exception ex) {
            LOG.warn("Failed to delete history sessions: " + ex.getMessage(), ex);
        }
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
