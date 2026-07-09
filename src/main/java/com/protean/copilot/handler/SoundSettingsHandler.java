package com.protean.copilot.handler;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.settings.CodemossSettingsService;

import java.awt.Toolkit;
import java.io.File;

final class SoundSettingsHandler {

    private static final Logger LOG = Logger.getInstance(SoundSettingsHandler.class);

    private final HandlerContext context;
    private final CodemossSettingsService settingsService = new CodemossSettingsService();
    private final Gson gson = new Gson();

    SoundSettingsHandler(HandlerContext context) {
        this.context = context;
    }

    void handleGetSoundNotificationConfig() {
        try {
            JsonObject response = new JsonObject();
            response.addProperty("enabled", settingsService.getSoundNotificationEnabled());
            response.addProperty("onlyWhenUnfocused", settingsService.getSoundOnlyWhenUnfocused());
            response.addProperty("selectedSound", settingsService.getSelectedSound());
            response.addProperty("customSoundPath", defaultString(settingsService.getCustomSoundPath()));
            pushConfig(response);
        } catch (Exception e) {
            LOG.warn("[SoundSettingsHandler] Failed to read config: " + e.getMessage(), e);
            JsonObject fallback = new JsonObject();
            fallback.addProperty("enabled", false);
            fallback.addProperty("onlyWhenUnfocused", false);
            fallback.addProperty("selectedSound", "default");
            fallback.addProperty("customSoundPath", "");
            pushConfig(fallback);
        }
    }

    void handleSetSoundNotificationEnabled(String content) {
        mutateBoolean(content, "enabled", false, settingsService::setSoundNotificationEnabled);
    }

    void handleSetSoundOnlyWhenUnfocused(String content) {
        mutateBoolean(content, "onlyWhenUnfocused", false, settingsService::setSoundOnlyWhenUnfocused);
    }

    void handleSetSelectedSound(String content) {
        try {
            JsonObject json = gson.fromJson(content, JsonObject.class);
            String soundId = json != null && json.has("soundId") && !json.get("soundId").isJsonNull()
                ? json.get("soundId").getAsString()
                : "default";
            settingsService.setSelectedSound(soundId);
            handleGetSoundNotificationConfig();
        } catch (Exception e) {
            LOG.warn("[SoundSettingsHandler] Failed to save selected sound: " + e.getMessage(), e);
        }
    }

    void handleSetCustomSoundPath(String content) {
        try {
            JsonObject json = gson.fromJson(content, JsonObject.class);
            String path = json != null && json.has("path") && !json.get("path").isJsonNull()
                ? json.get("path").getAsString().trim()
                : "";
            if (!path.isEmpty()) {
                String error = validateAudioFile(path);
                if (error != null) {
                    showError(error);
                    return;
                }
            }
            settingsService.setCustomSoundPath(path.isEmpty() ? null : path);
            handleGetSoundNotificationConfig();
            showSuccessI18n("settings.basic.soundNotification.customSoundSaved");
        } catch (Exception e) {
            LOG.warn("[SoundSettingsHandler] Failed to save custom sound path: " + e.getMessage(), e);
            showError("Failed to save custom sound path: " + e.getMessage());
        }
    }

    void handleTestSound(String content) {
        Toolkit.getDefaultToolkit().beep();
    }

    void handleBrowseSoundFile() {
        ApplicationManager.getApplication().invokeLater(() -> {
            FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false);
            descriptor.withTitle("Select Sound File");
            descriptor.withFileFilter(file -> {
                String ext = file.getExtension();
                return ext != null && (ext.equalsIgnoreCase("wav")
                    || ext.equalsIgnoreCase("mp3")
                    || ext.equalsIgnoreCase("aiff"));
            });
            FileChooser.chooseFile(descriptor, context.getProject(), null, file -> {
                if (file == null) {
                    return;
                }
                try {
                    settingsService.setCustomSoundPath(file.getPath());
                    settingsService.setSelectedSound("custom");
                    handleGetSoundNotificationConfig();
                } catch (Exception e) {
                    showError("Failed to save selected sound file: " + e.getMessage());
                }
            });
        });
    }

    private void mutateBoolean(String content, String field, boolean fallback, ThrowingBooleanConsumer consumer) {
        try {
            JsonObject json = gson.fromJson(content, JsonObject.class);
            boolean value = json != null && json.has(field) && !json.get(field).isJsonNull()
                ? json.get(field).getAsBoolean()
                : fallback;
            consumer.accept(value);
            handleGetSoundNotificationConfig();
        } catch (Exception e) {
            LOG.warn("[SoundSettingsHandler] Failed to mutate sound config: " + e.getMessage(), e);
            showError("Failed to save sound config: " + e.getMessage());
        }
    }

    private void pushConfig(JsonObject config) {
        ApplicationManager.getApplication().invokeLater(() ->
            context.callJavaScript("window.updateSoundNotificationConfig", context.escapeJs(config.toString())));
    }

    private void showError(String message) {
        ApplicationManager.getApplication().invokeLater(() ->
            context.callJavaScript("window.showError", context.escapeJs(message)));
    }

    private void showSuccessI18n(String key) {
        ApplicationManager.getApplication().invokeLater(() ->
            context.callJavaScript("window.showSuccessI18n", context.escapeJs(key)));
    }

    private static String validateAudioFile(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return "Audio file does not exist: " + path;
        }
        if (file.isDirectory()) {
            return "Audio file path is a directory: " + path;
        }
        return null;
    }

    private static String defaultString(String value) {
        return value != null ? value : "";
    }

    @FunctionalInterface
    private interface ThrowingBooleanConsumer {
        void accept(boolean value) throws Exception;
    }
}
