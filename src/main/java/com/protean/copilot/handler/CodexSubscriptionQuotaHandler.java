package com.protean.copilot.handler;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.protean.copilot.handler.core.HandlerContext;

final class CodexSubscriptionQuotaHandler {

    private final HandlerContext context;

    CodexSubscriptionQuotaHandler(HandlerContext context) {
        this.context = context;
    }

    void handleGetCodexSubscriptionQuota() {
        JsonObject payload = new JsonObject();
        payload.addProperty("status", "unavailable");
        payload.addProperty("fetchedAt", System.currentTimeMillis());
        payload.addProperty("source", "none");
        payload.addProperty("reasonCode", "api_key_mode");
        payload.addProperty("error", "API key mode has no subscription quota");
        payload.add("windows", buildEmptyWindows());
        ApplicationManager.getApplication().invokeLater(() ->
            context.callJavaScript("window.updateCodexSubscriptionQuota", context.escapeJs(payload.toString())));
    }

    private JsonObject buildEmptyWindows() {
        JsonObject windows = new JsonObject();
        windows.add("fiveHour", buildWindow("5h", 5));
        windows.add("weekly", buildWindow("weekly", 168));
        return windows;
    }

    private JsonObject buildWindow(String label, int hours) {
        JsonObject window = new JsonObject();
        window.addProperty("windowLabel", label);
        window.addProperty("windowHours", hours);
        window.add("usedPercent", JsonNull.INSTANCE);
        window.add("remainingPercent", JsonNull.INSTANCE);
        window.add("resetsAt", JsonNull.INSTANCE);
        window.addProperty("usedTokens", 0);
        window.add("limitTokens", JsonNull.INSTANCE);
        window.add("remainingTokens", JsonNull.INSTANCE);
        return window;
    }
}
