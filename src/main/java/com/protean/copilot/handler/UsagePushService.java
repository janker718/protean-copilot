package com.protean.copilot.handler;

import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.protean.copilot.handler.core.HandlerContext;

public final class UsagePushService {

    private final HandlerContext context;

    public UsagePushService(HandlerContext context) {
        this.context = context;
    }

    public void pushUsageUpdateAfterModelChange(int maxTokens) {
        JsonObject payload = new JsonObject();
        payload.addProperty("percentage", 0);
        payload.addProperty("usedTokens", 0);
        payload.addProperty("maxTokens", maxTokens);
        ApplicationManager.getApplication().invokeLater(() ->
            context.callJavaScript("window.onUsageUpdate", context.escapeJs(payload.toString())));
    }

    public void refreshContextBar() {
        pushUsageUpdateAfterModelChange(0);
    }
}
