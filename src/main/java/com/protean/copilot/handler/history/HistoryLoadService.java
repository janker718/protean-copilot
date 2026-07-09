package com.protean.copilot.handler.history;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.protean.copilot.cache.SessionIndexEntry;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.history.HistoryIndexService;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Comparator;
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
        List<SessionIndexEntry> entries = HistoryIndexService.getInstance(context.project)
            .listEntries()
            .stream()
            .filter(entry -> normalizedProvider == null || normalizedProvider.equals(normalize(entry.provider())))
            .sorted(Comparator.comparingLong(SessionIndexEntry::updatedAt).reversed())
            .toList();

        JsonArray sessions = new JsonArray();
        for (SessionIndexEntry entry : entries) {
            sessions.add(toHistorySessionSummary(entry));
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("success", true);
        payload.add("sessions", sessions);
        payload.addProperty("total", entries.size());

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
        session.addProperty("messageCount", 1);
        session.addProperty("lastTimestamp", Instant.ofEpochMilli(entry.updatedAt()).toString());
        session.addProperty("provider", normalize(entry.provider()) == null ? "claude" : normalize(entry.provider()));
        session.addProperty("isFavorited", entry.favorited());
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
