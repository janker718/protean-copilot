package com.protean.copilot.handler.provider;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.protean.copilot.handler.UsagePushService;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.session.ChatSession;

import java.util.HashMap;
import java.util.Map;

public final class ModelProviderHandler {

    private static final Logger LOG = Logger.getInstance(ModelProviderHandler.class);
    private static final Map<String, Integer> MODEL_CONTEXT_LIMITS = new HashMap<>();

    static {
        MODEL_CONTEXT_LIMITS.put("claude-sonnet-4-6", 200_000);
        MODEL_CONTEXT_LIMITS.put("claude-sonnet-4-6[1m]", 1_000_000);
        MODEL_CONTEXT_LIMITS.put("claude-opus-4-8", 200_000);
        MODEL_CONTEXT_LIMITS.put("claude-opus-4-8[1m]", 1_000_000);
        MODEL_CONTEXT_LIMITS.put("gpt-5.5", 1_000_000);
        MODEL_CONTEXT_LIMITS.put("gpt-5.4", 1_000_000);
        MODEL_CONTEXT_LIMITS.put("gpt-5.4-mini", 400_000);
        MODEL_CONTEXT_LIMITS.put("gpt-5.3-codex", 258_000);
        MODEL_CONTEXT_LIMITS.put("gpt-4o", 128_000);
        MODEL_CONTEXT_LIMITS.put("o3", 200_000);
    }

    private final HandlerContext context;
    private final UsagePushService usagePushService;
    private final Gson gson = new Gson();

    public ModelProviderHandler(HandlerContext context, UsagePushService usagePushService) {
        this.context = context;
        this.usagePushService = usagePushService;
    }

    public void handleSetModel(String content) {
        String model = extractString(content, "model");
        context.setCurrentModel(model);
        ChatSession session = context.getSession();
        if (session != null) {
            session.setModel(model);
        }
        int maxTokens = getModelContextLimit(model);
        ApplicationManager.getApplication().invokeLater(() -> {
            context.callJavaScript("window.onModelConfirmed", context.escapeJs(model), context.escapeJs(context.getCurrentProvider()));
            usagePushService.pushUsageUpdateAfterModelChange(maxTokens);
        });
    }

    public void handleSetProvider(String content) {
        String provider = extractString(content, "provider");
        context.setCurrentProvider(provider);
        ChatSession session = context.getSession();
        if (session != null) {
            session.setProvider(provider);
        }
    }

    public void handleSetReasoningEffort(String content) {
        ChatSession session = context.getSession();
        if (session != null) {
            session.setReasoningEffort(extractString(content, "reasoningEffort"));
        }
    }

    public void handleSetCodexFastMode(String content) {
        LOG.debug("[ModelProviderHandler] Ignoring codex fast mode payload in current bridge: " + content);
    }

    public static int getModelContextLimit(String model) {
        return MODEL_CONTEXT_LIMITS.getOrDefault(model, 200_000);
    }

    private String extractString(String content, String key) {
        if (content == null || content.isBlank()) {
            return "";
        }
        try {
            JsonObject json = gson.fromJson(content, JsonObject.class);
            if (json != null && json.has(key) && !json.get(key).isJsonNull()) {
                return json.get(key).getAsString();
            }
        } catch (Exception ignored) {
            // Fallback to raw string.
        }
        return content;
    }
}
