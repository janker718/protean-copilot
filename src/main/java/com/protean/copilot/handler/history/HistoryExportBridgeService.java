package com.protean.copilot.handler.history;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.history.HistoryExportService;
import com.protean.copilot.history.HistoryIndexService;
import com.protean.copilot.provider.claude.ClaudeHistoryReader;
import com.protean.copilot.provider.codex.CodexHistoryReader;
import com.protean.copilot.session.ChatSession;
import com.protean.copilot.session.MessageParser;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Exports the currently active session through the existing webview callback.
 */
public final class HistoryExportBridgeService {

    private static final Logger LOG = Logger.getInstance(HistoryExportBridgeService.class);

    private final HandlerContext context;
    private final Gson gson = new Gson();
    private final MessageParser messageParser = new MessageParser();

    public HistoryExportBridgeService(@NotNull HandlerContext context) {
        this.context = context;
    }

    public void handleExportSession(String content) {
        try {
            JsonObject payload = JsonParser.parseString(content).getAsJsonObject();
            String sessionId = payload.has("sessionId") ? payload.get("sessionId").getAsString() : null;
            String title = payload.has("title") ? payload.get("title").getAsString() : "session";

            ChatSession session = findCurrentSession();
            if (sessionId == null) {
                context.callJavaScript("addErrorMessage", "Missing sessionId for export.");
                return;
            }

            String markdown;
            if (session != null && sessionId.equals(session.getSessionId())) {
                markdown = HistoryExportService.getInstance(context.project).exportAsMarkdown(session);
            } else {
                String preferredProjectPath = session != null ? session.getCwd() : context.project.getBasePath();
                String preferredProvider = payload.has("provider") ? payload.get("provider").getAsString() : null;
                String projectPath = preferredProjectPath;
                var entry = HistoryIndexService.getInstance(context.project).getEntry(sessionId, preferredProjectPath, preferredProvider);
                if (entry != null && entry.workingDirectory() != null && !entry.workingDirectory().isBlank()) {
                    projectPath = entry.workingDirectory();
                }
                String provider = entry != null ? entry.provider() : preferredProvider;
                String rawJson = loadHistoryMessages(provider, projectPath, sessionId);
                markdown = HistoryExportService.getInstance(context.project)
                    .exportAsMarkdown(sessionId, parseHistoryMessages(rawJson));
            }

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

    private String loadHistoryMessages(String provider, String projectPath, String sessionId) {
        String normalized = provider == null || provider.isBlank() ? "claude" : provider.trim();
        if ("codex".equalsIgnoreCase(normalized)) {
            return new CodexHistoryReader().getSessionMessagesAsJson(sessionId, projectPath);
        }
        return new ClaudeHistoryReader().getSessionMessagesAsJson(projectPath, sessionId);
    }

    private List<ChatSession.Message> parseHistoryMessages(String rawJson) {
        List<ChatSession.Message> messages = new ArrayList<>();
        JsonElement parsed = JsonParser.parseString(rawJson);
        if (!parsed.isJsonArray()) {
            return messages;
        }

        JsonArray items = parsed.getAsJsonArray();
        for (JsonElement element : items) {
            if (!element.isJsonObject()) {
                continue;
            }
            ChatSession.Message parsedMessage = messageParser.parseServerMessage(element.getAsJsonObject());
            if (parsedMessage != null) {
                messages.add(parsedMessage);
            }
        }
        return messages;
    }
}
