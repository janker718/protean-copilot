package com.protean.copilot.handler.history;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;
import com.protean.copilot.cache.SessionIndexEntry;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.history.HistoryIndexService;
import com.protean.copilot.session.HistorySessionLoadRequest;
import com.protean.copilot.session.SessionLifecycleManager;
import com.protean.copilot.session.SessionRuntimeMessages;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves a selected history row back into a session load operation.
 */
public final class HistoryMessageInjector {

    private static final Logger LOG = Logger.getInstance(HistoryMessageInjector.class);

    private final HandlerContext context;
    private final SessionLifecycleManager sessionLifecycleManager;

    public HistoryMessageInjector(
        @NotNull HandlerContext context,
        @NotNull SessionLifecycleManager sessionLifecycleManager
    ) {
        this.context = context;
        this.sessionLifecycleManager = sessionLifecycleManager;
    }

    public void handleLoadSession(String content, String currentProvider) {
        try {
            JsonObject payload = JsonParser.parseString(content).getAsJsonObject();
            String sessionId = payload.has("sessionId") ? normalize(payload.get("sessionId").getAsString()) : null;
            String provider = payload.has("provider") ? normalize(payload.get("provider").getAsString()) : null;
            if (provider == null) {
                provider = normalize(currentProvider);
            }

            if (sessionId == null) {
                context.callJavaScript("historyLoadComplete");
                context.callJavaScript("addErrorMessage", SessionRuntimeMessages.historySessionIdMissing());
                return;
            }

            String preferredProjectPath = context.getSession() != null
                ? context.getSession().getCwd()
                : context.project.getBasePath();
            SessionIndexEntry entry = HistoryIndexService.getInstance(context.project)
                .getEntry(sessionId, preferredProjectPath, provider);
            String projectPath = entry != null
                ? entry.workingDirectory()
                : preferredProjectPath;

            LOG.info("[HistoryHandler] Loading history session: " + sessionId + ", provider=" + provider);
            sessionLifecycleManager.loadHistorySession(HistorySessionLoadRequest.of(sessionId, projectPath, provider));
        } catch (Exception ex) {
            LOG.warn("Failed to load history session: " + ex.getMessage(), ex);
            context.callJavaScript("historyLoadComplete");
            context.callJavaScript("addErrorMessage",
                SessionRuntimeMessages.historyResumeFailed(currentProvider, ex));
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
