package com.protean.copilot.handler;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.protean.copilot.bridge.NodeDetector;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.provider.claude.NodeDetectionResult;
import com.protean.copilot.settings.SettingsService;

final class NodePathHandler {

    private static final Logger LOG = Logger.getInstance(NodePathHandler.class);
    private static final int MIN_NODE_VERSION = 18;

    private final HandlerContext context;
    private final Gson gson = new Gson();
    private final SettingsService settingsService = new SettingsService();
    private final NodeDetector nodeDetector = NodeDetector.getInstance();

    NodePathHandler(HandlerContext context) {
        this.context = context;
    }

    void handleGetNodePath() {
        String path = settingsService.getNodePath();
        NodeDetectionResult result = nodeDetector.verifyNodePath(path);
        push(path, result.version());
    }

    void handleSetNodePath(String content) {
        try {
            JsonObject json = gson.fromJson(content, JsonObject.class);
            String path = json != null && json.has("path") && !json.get("path").isJsonNull()
                ? json.get("path").getAsString().trim()
                : "";
            String resolved = path.isEmpty() ? "node" : path;
            NodeDetectionResult result = nodeDetector.verifyAndCacheNodePath(resolved);
            if (!result.available()) {
                showError(result.version());
                push(resolved, null);
                return;
            }
            settingsService.setNodePath(resolved);
            push(resolved, result.version());
            showSuccess("Node.js path saved");
        } catch (Exception e) {
            LOG.warn("[NodePathHandler] Failed to save node path: " + e.getMessage(), e);
            showError("Failed to save Node.js path: " + e.getMessage());
        }
    }

    private void push(String path, String version) {
        JsonObject payload = new JsonObject();
        payload.addProperty("path", path);
        payload.addProperty("version", version != null ? version : "");
        payload.addProperty("minVersion", MIN_NODE_VERSION);
        ApplicationManager.getApplication().invokeLater(() ->
            context.callJavaScript("window.updateNodePath", context.escapeJs(payload.toString())));
    }

    private void showError(String message) {
        ApplicationManager.getApplication().invokeLater(() ->
            context.callJavaScript("window.showError", context.escapeJs(message)));
    }

    private void showSuccess(String message) {
        ApplicationManager.getApplication().invokeLater(() ->
            context.callJavaScript("window.showSwitchSuccess", context.escapeJs(message)));
    }
}
