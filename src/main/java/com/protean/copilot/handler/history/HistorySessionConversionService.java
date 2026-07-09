package com.protean.copilot.handler.history;

import com.google.gson.JsonObject;
import com.intellij.openapi.diagnostic.Logger;
import com.protean.copilot.cache.SessionIndexEntry;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.history.HistoryIndexService;
import com.protean.copilot.history.HistoryMetadataService;
import com.protean.copilot.session.ChatSession;
import org.jetbrains.annotations.NotNull;

/**
 * Converts a stored session entry from sdk-cli to cli in the local history index.
 */
public final class HistorySessionConversionService {

    private static final Logger LOG = Logger.getInstance(HistorySessionConversionService.class);

    private final HandlerContext context;

    public HistorySessionConversionService(@NotNull HandlerContext context) {
        this.context = context;
    }

    public void handleConvertToCliSession(String content) {
        String sessionId = normalize(content);
        if (sessionId == null) {
            notifyResult(false, null, "INVALID_SESSION_ID");
            return;
        }

        ChatSession currentSession = context.getSession();
        if (currentSession != null && sessionId.equals(currentSession.getSessionId())) {
            notifyResult(false, null, "SESSION_ACTIVE");
            return;
        }

        SessionIndexEntry entry = HistoryIndexService.getInstance(context.project).getEntry(sessionId);
        if (entry == null) {
            notifyResult(false, null, "SESSION_NOT_FOUND");
            return;
        }

        String entrypoint = normalizeEntrypoint(entry.entrypoint());
        if ("cli".equals(entrypoint)) {
            notifyResult(true, "ALREADY_CLI_SESSION", null);
            return;
        }
        if (!"sdk-cli".equals(entrypoint) && !"claude-vscode".equals(entrypoint)) {
            notifyResult(false, null, "NOT_SDK_SESSION");
            return;
        }

        boolean updated = HistoryMetadataService.getInstance(context.project).updateEntrypoint(sessionId, "cli");
        if (!updated) {
            notifyResult(false, null, "CONVERSION_FAILED");
            return;
        }

        LOG.info("[HistoryHandler] Converted history session to cli: " + sessionId);
        notifyResult(true, null, null);
    }

    private void notifyResult(boolean success, String infoCode, String errorCode) {
        JsonObject payload = new JsonObject();
        payload.addProperty("success", success);
        if (infoCode != null) {
            payload.addProperty("infoCode", infoCode);
        }
        if (errorCode != null) {
            payload.addProperty("errorCode", errorCode);
        }
        context.callJavaScript("onConversionResult", payload.toString());
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeEntrypoint(String value) {
        if (value == null || value.isBlank()) {
            return "sdk-cli";
        }
        return value.trim();
    }
}
