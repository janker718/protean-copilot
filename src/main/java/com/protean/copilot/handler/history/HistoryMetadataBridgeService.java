package com.protean.copilot.handler.history;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.history.HistoryMetadataService;
import org.jetbrains.annotations.NotNull;

/**
 * Applies history metadata changes initiated by the webview.
 */
public final class HistoryMetadataBridgeService {

    private static final Logger LOG = Logger.getInstance(HistoryMetadataBridgeService.class);
    private static final int MAX_CUSTOM_TITLE_LENGTH = 50;

    private final HandlerContext context;

    public HistoryMetadataBridgeService(@NotNull HandlerContext context) {
        this.context = context;
    }

    public void handleToggleFavorite(String content) {
        String sessionId = normalize(content);
        if (sessionId == null) {
            return;
        }
        HistoryMetadataService.getInstance(context.project).toggleFavorite(sessionId);
    }

    public void handleUpdateTitle(String content) {
        try {
            JsonObject payload = JsonParser.parseString(content).getAsJsonObject();
            String sessionId = payload.has("sessionId") ? normalize(payload.get("sessionId").getAsString()) : null;
            String customTitle = payload.has("customTitle") ? normalize(payload.get("customTitle").getAsString()) : null;
            if (sessionId == null) {
                return;
            }
            if (customTitle != null && customTitle.length() > MAX_CUSTOM_TITLE_LENGTH) {
                context.callJavaScript("addErrorMessage", "Custom title exceeds max length of " + MAX_CUSTOM_TITLE_LENGTH);
                return;
            }
            HistoryMetadataService.getInstance(context.project).updateCustomTitle(sessionId, customTitle);
        } catch (Exception ex) {
            LOG.warn("Failed to update history title: " + ex.getMessage(), ex);
            context.callJavaScript("addErrorMessage", "Failed to update history title: " + ex.getMessage());
        }
    }

    public void handleDeleteTitle(String content) {
        String sessionId = normalize(content);
        if (sessionId == null) {
            return;
        }
        HistoryMetadataService.getInstance(context.project).deleteCustomTitle(sessionId);
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
