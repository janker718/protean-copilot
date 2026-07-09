package com.protean.copilot.handler.history;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.history.HistoryExportService;
import com.protean.copilot.session.ChatSession;
import org.jetbrains.annotations.NotNull;

/**
 * Exports the currently active session through the existing webview callback.
 */
public final class HistoryExportBridgeService {

    private static final Logger LOG = Logger.getInstance(HistoryExportBridgeService.class);

    private final HandlerContext context;
    private final Gson gson = new Gson();

    public HistoryExportBridgeService(@NotNull HandlerContext context) {
        this.context = context;
    }

    public void handleExportSession(String content) {
        try {
            JsonObject payload = JsonParser.parseString(content).getAsJsonObject();
            String sessionId = payload.has("sessionId") ? payload.get("sessionId").getAsString() : null;
            String title = payload.has("title") ? payload.get("title").getAsString() : "session";

            ChatSession session = findCurrentSession();
            if (session == null || sessionId == null || !sessionId.equals(session.getSessionId())) {
                context.callJavaScript("addErrorMessage", "Export currently supports the active session only.");
                return;
            }

            String markdown = HistoryExportService.getInstance(context.project).exportAsMarkdown(session);
            JsonObject exportData = new JsonObject();
            exportData.addProperty("sessionId", sessionId);
            exportData.addProperty("title", title);
            exportData.addProperty("content", markdown);

            context.executeJavaScriptOnEDT(
                "window.onExportSessionData && window.onExportSessionData(" + gson.toJson(exportData) + ");"
            );
        } catch (Exception ex) {
            LOG.warn("Failed to export session: " + ex.getMessage(), ex);
            context.callJavaScript("addErrorMessage", "Failed to export session: " + ex.getMessage());
        }
    }

    private ChatSession findCurrentSession() {
        return context.getSession();
    }
}
