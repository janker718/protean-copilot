package com.protean.copilot.handler;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.protean.copilot.handler.core.HandlerContext;

import java.io.File;

final class ClaudeCliPathHandler {

    private static final Logger LOG = Logger.getInstance(ClaudeCliPathHandler.class);
    private static final String CLAUDE_CLI_PATH_PROPERTY_KEY = "protean.claudeCliPath";

    private final HandlerContext context;
    private final Gson gson = new Gson();

    ClaudeCliPathHandler(HandlerContext context) {
        this.context = context;
    }

    void handleGetClaudeCliPath() {
        JsonObject payload = new JsonObject();
        payload.addProperty("path", PropertiesComponent.getInstance().getValue(CLAUDE_CLI_PATH_PROPERTY_KEY, ""));
        ApplicationManager.getApplication().invokeLater(() ->
            context.callJavaScript("window.updateClaudeCliPath", context.escapeJs(payload.toString())));
    }

    void handleSetClaudeCliPath(String content) {
        try {
            JsonObject json = gson.fromJson(content, JsonObject.class);
            String path = json != null && json.has("path") && !json.get("path").isJsonNull()
                ? json.get("path").getAsString().trim()
                : "";

            String error = validateCliPath(path);
            if (error != null) {
                showError(error);
                handleGetClaudeCliPath();
                return;
            }

            PropertiesComponent props = PropertiesComponent.getInstance();
            if (path.isEmpty()) {
                props.unsetValue(CLAUDE_CLI_PATH_PROPERTY_KEY);
            } else {
                props.setValue(CLAUDE_CLI_PATH_PROPERTY_KEY, path);
            }

            try {
                if (context.sdkBridge.getClaudeBridge() != null) {
                    context.sdkBridge.getClaudeBridge().shutdownDaemon();
                }
            } catch (Exception e) {
                LOG.warn("[ClaudeCliPathHandler] Failed to shutdown daemon: " + e.getMessage(), e);
            }

            handleGetClaudeCliPath();
            showSuccess(path.isEmpty() ? "Claude CLI path cleared" : "Claude CLI path saved");
        } catch (Exception e) {
            LOG.warn("[ClaudeCliPathHandler] Failed to save Claude CLI path: " + e.getMessage(), e);
            showError("Failed to save Claude CLI path: " + e.getMessage());
        }
    }

    private static String validateCliPath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        File file = new File(rawPath);
        if (!file.exists()) {
            return "File does not exist: " + rawPath;
        }
        if (file.isDirectory()) {
            return "Path is a directory: " + rawPath;
        }
        if (!file.canExecute()) {
            return "File is not executable: " + rawPath;
        }
        return null;
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
