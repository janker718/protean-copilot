package com.protean.copilot.handler;

import com.google.gson.JsonObject;
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
        // callJavaScript owns JavaScript-string escaping and EDT dispatch. Pre-escaping
        // here turns JSON into a literal such as {\"percentage\":0}, which the WebView
        // callback cannot parse.
        context.callJavaScript("window.onUsageUpdate", payload.toString());
    }

    public void refreshContextBar() {
        pushUsageUpdateAfterModelChange(0);
    }
}
