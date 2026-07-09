package com.protean.copilot.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;

/**
 * Shared permission dialog timeout settings helpers.
 */
public final class PermissionDialogTimeoutSettings {

    public static final int MIN_PERMISSION_DIALOG_TIMEOUT_SECONDS = 30;
    public static final int MAX_PERMISSION_DIALOG_TIMEOUT_SECONDS = 3600;
    public static final int DEFAULT_PERMISSION_DIALOG_TIMEOUT_SECONDS = 300;
    public static final long PERMISSION_SAFETY_NET_BUFFER_SECONDS = 60L;

    private static final String KEY_PERMISSION_DIALOG_TIMEOUT_SECONDS = "permissionDialogTimeoutSeconds";

    private PermissionDialogTimeoutSettings() {
    }

    public static int getPermissionDialogTimeoutSeconds(CodemossSettingsService service) throws IOException {
        JsonObject config = service.readConfig();
        if (!config.has(KEY_PERMISSION_DIALOG_TIMEOUT_SECONDS)) {
            config.addProperty(KEY_PERMISSION_DIALOG_TIMEOUT_SECONDS, DEFAULT_PERMISSION_DIALOG_TIMEOUT_SECONDS);
            service.writeConfig(config);
            return DEFAULT_PERMISSION_DIALOG_TIMEOUT_SECONDS;
        }

        JsonElement element = config.get(KEY_PERMISSION_DIALOG_TIMEOUT_SECONDS);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            return DEFAULT_PERMISSION_DIALOG_TIMEOUT_SECONDS;
        }

        return clamp(element.getAsInt());
    }

    public static void setPermissionDialogTimeoutSeconds(CodemossSettingsService service, int seconds) throws IOException {
        JsonObject config = service.readConfig();
        config.addProperty(KEY_PERMISSION_DIALOG_TIMEOUT_SECONDS, clamp(seconds));
        service.writeConfig(config);
    }

    public static int clamp(int seconds) {
        if (seconds < MIN_PERMISSION_DIALOG_TIMEOUT_SECONDS) {
            return MIN_PERMISSION_DIALOG_TIMEOUT_SECONDS;
        }
        if (seconds > MAX_PERMISSION_DIALOG_TIMEOUT_SECONDS) {
            return MAX_PERMISSION_DIALOG_TIMEOUT_SECONDS;
        }
        return seconds;
    }
}
