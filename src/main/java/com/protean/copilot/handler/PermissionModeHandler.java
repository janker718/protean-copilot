package com.protean.copilot.handler;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.settings.CodemossSettingsService;

final class PermissionModeHandler {

    private static final Logger LOG = Logger.getInstance(PermissionModeHandler.class);
    private static final Gson GSON = new Gson();

    private final HandlerContext context;
    private final CodemossSettingsService settingsService = new CodemossSettingsService();

    PermissionModeHandler(HandlerContext context) {
        this.context = context;
    }

    void handleGetMode() {
        String mode = normalize(settingsService.getPermissionMode());
        ApplicationManager.getApplication().invokeLater(() ->
            context.callJavaScript("window.onModeReceived", context.escapeJs(mode)));
    }

    void handleSetMode(String content) {
        try {
            String mode = normalize(extractValue(content, "mode"));
            settingsService.setPermissionMode(mode);
            if (context.getPermissionService() != null) {
                context.getPermissionService().setPermissionMode(mode);
            }
            ApplicationManager.getApplication().invokeLater(() ->
                context.callJavaScript("window.onModeChanged", context.escapeJs(mode)));
        } catch (Exception e) {
            LOG.warn("[PermissionModeHandler] Failed to set mode: " + e.getMessage(), e);
        }
    }

    private static String extractValue(String content, String key) {
        if (content == null || content.isBlank()) {
            return "";
        }
        try {
            JsonObject json = GSON.fromJson(content, JsonObject.class);
            if (json != null && json.has(key) && !json.get(key).isJsonNull()) {
                return json.get(key).getAsString();
            }
        } catch (Exception ignored) {
            // Fallback to raw content.
        }
        return content;
    }

    private static String normalize(String mode) {
        return switch (mode) {
            case "default", "acceptEdits", "plan", "bypassPermissions" -> mode;
            default -> "bypassPermissions";
        };
    }
}
