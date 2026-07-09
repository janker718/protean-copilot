package com.protean.copilot.handler.history;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.protean.copilot.cache.SessionIndexEntry;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.history.HistoryIndexService;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * Loads minimal history list data from the in-memory session index.
 */
public final class HistoryLoadService {

    private final HandlerContext context;
    private final Gson gson = new Gson();

    public HistoryLoadService(@NotNull HandlerContext context) {
        this.context = context;
    }

    public void handleLoadHistoryData(String providerFilter) {
        String normalizedProvider = normalize(providerFilter);
        if (normalizedProvider != null && !"claude".equals(normalizedProvider)) {
            JsonObject payload = new JsonObject();
            payload.addProperty("success", true);
            payload.add("sessions", new JsonArray());
            payload.addProperty("total", 0);
            context.executeJavaScriptOnEDT(
                "window.setHistoryData && window.setHistoryData(" + gson.toJson(payload) + ");"
            );
            return;
        }

        String projectPath = context.getSession() != null
            ? context.getSession().getCwd()
            : context.project.getBasePath();
        List<SessionIndexEntry> entries = HistoryIndexService.getInstance(context.project)
            .listEntries(projectPath, normalizedProvider);

        JsonArray sessions = new JsonArray();
        int totalMessages = 0;
        for (SessionIndexEntry entry : entries) {
            sessions.add(toHistorySessionSummary(entry));
            totalMessages += Math.max(0, entry.messageCount());
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("success", true);
        payload.add("sessions", sessions);
        payload.addProperty("currentProject", projectPath);
        payload.addProperty("total", totalMessages);
        payload.addProperty("sessionCount", entries.size());

        context.executeJavaScriptOnEDT(
            "window.setHistoryData && window.setHistoryData(" + gson.toJson(payload) + ");"
        );
    }

    public void handleDeepSearchHistory(String providerFilter) {
        handleLoadHistoryData(providerFilter);
    }

    private JsonObject toHistorySessionSummary(SessionIndexEntry entry) {
        JsonObject session = new JsonObject();
        session.addProperty("sessionId", entry.sessionId());
        session.addProperty("title", entry.customTitle() == null || entry.customTitle().isBlank()
            ? (entry.summary() == null || entry.summary().isBlank()
                ? "Untitled session"
                : entry.summary())
            : entry.customTitle());
        session.addProperty("messageCount", Math.max(entry.messageCount(), 0));
        session.addProperty("lastTimestamp", Instant.ofEpochMilli(entry.updatedAt()).toString());
        session.addProperty("provider", normalize(entry.provider()) == null ? "claude" : normalize(entry.provider()));
        session.addProperty("isFavorited", entry.favorited());
        session.addProperty("fileSize", Math.max(entry.fileSize(), 0L));
        if (entry.favoritedAt() > 0) {
            session.addProperty("favoritedAt", entry.favoritedAt());
        }
        if (entry.entrypoint() != null && !entry.entrypoint().isBlank()) {
            session.addProperty("entrypoint", entry.entrypoint());
        }
        return session;
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
