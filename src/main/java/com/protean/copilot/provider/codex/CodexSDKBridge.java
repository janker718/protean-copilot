package com.protean.copilot.provider.codex;

import com.google.gson.JsonObject;
import com.protean.copilot.provider.common.BaseSDKBridge;

import java.util.concurrent.CompletableFuture;

public class CodexSDKBridge extends BaseSDKBridge {

    @Override
    protected String getProviderName() {
        return "Codex";
    }

    @Override
    protected String getDefaultModel() {
        return "gpt-5.5";
    }

    @Override
    protected String getBridgeScriptResource() {
        return "bridge/codex-sdk-bridge.mjs";
    }

    @Override
    protected void handleReady(JsonObject message) {
        super.handleReady(message);
        boolean sdkAvailable = message.has("sdkAvailable") && message.get("sdkAvailable").getAsBoolean();
        if (sdkAvailable) {
            log().info("Codex bridge ready");
        } else {
            log().warn("Codex bridge ready, but runtime backend is not bundled yet");
        }
    }

    @Override
    public CompletableFuture<Void> query(
        String sessionId,
        String prompt,
        String cwd,
        String model,
        String permissionMode,
        String reasoningEffort
    ) {
        String effectiveModel = (model == null || model.isBlank()) ? getDefaultModel() : model;
        String effectivePermissionMode = (permissionMode == null || permissionMode.isBlank()) ? "default" : permissionMode;
        return super.query(sessionId, prompt, cwd, effectiveModel, effectivePermissionMode, reasoningEffort);
    }
}
