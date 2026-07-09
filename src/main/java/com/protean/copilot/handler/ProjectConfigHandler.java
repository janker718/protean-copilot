package com.protean.copilot.handler;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.settings.CodemossSettingsService;
import com.protean.copilot.util.ThemeConfigService;

import javax.swing.UIManager;
import java.io.File;
import java.nio.file.Files;
import java.util.Base64;

final class ProjectConfigHandler {

    private static final Logger LOG = Logger.getInstance(ProjectConfigHandler.class);
    private static final String SEND_SHORTCUT_PROPERTY_KEY = "protean.sendShortcut";

    private final HandlerContext context;
    private final CodemossSettingsService settingsService = new CodemossSettingsService();
    private final Gson gson = new Gson();

    ProjectConfigHandler(HandlerContext context) {
        this.context = context;
    }

    void handleGetUsageStatistics(String content) {
        JsonObject payload = new JsonObject();
        String projectPath = context.getProject().getBasePath();
        payload.addProperty("projectPath", projectPath != null ? projectPath : "");
        payload.addProperty("projectName", context.getProject().getName());
        payload.addProperty("totalSessions", 0);
        payload.add("totalUsage", zeroUsage());
        payload.addProperty("estimatedCost", 0);
        payload.add("sessions", new JsonArray());
        payload.add("dailyUsage", new JsonArray());
        payload.add("byModel", new JsonArray());
        JsonObject comparison = new JsonObject();
        comparison.add("currentWeek", weekBlock());
        comparison.add("lastWeek", weekBlock());
        comparison.add("trends", weekBlock());
        payload.add("weeklyComparison", comparison);
        payload.addProperty("lastUpdated", System.currentTimeMillis());
        pushJson("window.updateUsageStatistics", payload);
    }

    void handleGetWorkingDirectory() {
        try {
            JsonObject payload = new JsonObject();
            String projectPath = context.getProject().getBasePath();
            payload.addProperty("projectPath", projectPath != null ? projectPath : "");
            payload.addProperty("customWorkingDir",
                projectPath != null && settingsService.getCustomWorkingDirectory(projectPath) != null
                    ? settingsService.getCustomWorkingDirectory(projectPath)
                    : "");
            pushJson("window.updateWorkingDirectory", payload);
        } catch (Exception e) {
            showError("Failed to get working directory: " + e.getMessage());
        }
    }

    void handleSetWorkingDirectory(String content) {
        try {
            String projectPath = context.getProject().getBasePath();
            if (projectPath == null) {
                showError("Unable to resolve project path");
                return;
            }
            JsonObject json = gson.fromJson(content, JsonObject.class);
            String customWorkingDir = readString(json, "customWorkingDir", "");
            if (!customWorkingDir.isBlank()) {
                File file = new File(customWorkingDir);
                if (!file.isAbsolute()) {
                    file = new File(projectPath, customWorkingDir);
                }
                if (!file.exists() || !file.isDirectory()) {
                    showError("Working directory does not exist: " + file.getAbsolutePath());
                    return;
                }
                customWorkingDir = file.getAbsolutePath();
            } else {
                customWorkingDir = null;
            }
            settingsService.setCustomWorkingDirectory(projectPath, customWorkingDir);
            handleGetWorkingDirectory();
            showSuccess("Working directory config saved");
        } catch (Exception e) {
            showError("Failed to save working directory: " + e.getMessage());
        }
    }

    void handleGetEditorFontConfig() {
        try {
            var scheme = EditorColorsManager.getInstance().getGlobalScheme();
            JsonObject payload = new JsonObject();
            payload.addProperty("fontFamily", scheme.getEditorFontName());
            payload.addProperty("fontSize", scheme.getEditorFontSize());
            payload.addProperty("lineSpacing", scheme.getLineSpacing());
            pushJson("window.onEditorFontConfigReceived", payload);
        } catch (Exception e) {
            showError("Failed to load editor font config: " + e.getMessage());
        }
    }

    void handleGetUiFontConfig() {
        try {
            pushJson("window.onUiFontConfigReceived", enrichFontConfig(settingsService.getUiFontConfig(), false));
        } catch (Exception e) {
            showError("Failed to load UI font config: " + e.getMessage());
        }
    }

    void handleSetUiFontConfig(String content) {
        try {
            settingsService.setUiFontConfig(gson.fromJson(content, JsonObject.class));
            handleGetUiFontConfig();
        } catch (Exception e) {
            showError("Failed to save UI font config: " + e.getMessage());
        }
    }

    void handleBrowseUiFontFile() {
        browseFontFile(path -> {
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("mode", "customFile");
                payload.addProperty("customFontPath", path);
                settingsService.setUiFontConfig(payload);
                handleGetUiFontConfig();
            } catch (Exception e) {
                showError("Failed to save UI font file: " + e.getMessage());
            }
        });
    }

    void handleGetCodeFontConfig() {
        try {
            pushJson("window.onCodeFontConfigReceived", enrichFontConfig(settingsService.getCodeFontConfig(), true));
        } catch (Exception e) {
            showError("Failed to load code font config: " + e.getMessage());
        }
    }

    void handleSetCodeFontConfig(String content) {
        try {
            settingsService.setCodeFontConfig(gson.fromJson(content, JsonObject.class));
            handleGetCodeFontConfig();
        } catch (Exception e) {
            showError("Failed to save code font config: " + e.getMessage());
        }
    }

    void handleBrowseCodeFontFile() {
        browseFontFile(path -> {
            try {
                JsonObject payload = new JsonObject();
                payload.addProperty("mode", "customFile");
                payload.addProperty("customFontPath", path);
                settingsService.setCodeFontConfig(payload);
                handleGetCodeFontConfig();
            } catch (Exception e) {
                showError("Failed to save code font file: " + e.getMessage());
            }
        });
    }

    void handleGetStreamingEnabled() {
        try {
            String projectPath = context.getProject().getBasePath();
            boolean enabled = projectPath == null || settingsService.getStreamingEnabled(projectPath);
            JsonObject payload = new JsonObject();
            payload.addProperty("streamingEnabled", enabled);
            pushJson("window.updateStreamingEnabled", payload);
        } catch (Exception e) {
            showError("Failed to load streaming config: " + e.getMessage());
        }
    }

    void handleSetStreamingEnabled(String content) {
        mutateProjectBoolean(content, "streamingEnabled", true, settingsService::setStreamingEnabled,
            "window.updateStreamingEnabled");
    }

    void handleGetCodexSandboxMode() {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("sandboxMode", settingsService.getCodexSandboxMode(context.getProject().getBasePath()));
            pushJson("window.updateCodexSandboxMode", payload);
        } catch (Exception e) {
            showError("Failed to load Codex sandbox mode: " + e.getMessage());
        }
    }

    void handleSetCodexSandboxMode(String content) {
        try {
            JsonObject json = gson.fromJson(content, JsonObject.class);
            String sandboxMode = readString(json, "sandboxMode", "danger-full-access");
            settingsService.setCodexSandboxMode(context.getProject().getBasePath(), sandboxMode);
            handleGetCodexSandboxMode();
            showSuccessI18n("toast.saveSuccess");
        } catch (Exception e) {
            showError("Failed to save Codex sandbox mode: " + e.getMessage());
        }
    }

    void handleGetSendShortcut() {
        JsonObject payload = new JsonObject();
        payload.addProperty("sendShortcut", PropertiesComponent.getInstance().getValue(SEND_SHORTCUT_PROPERTY_KEY, "enter"));
        pushJson("window.updateSendShortcut", payload);
    }

    void handleSetSendShortcut(String content) {
        JsonObject json = gson.fromJson(content, JsonObject.class);
        String shortcut = readString(json, "sendShortcut", "enter");
        String normalized = "cmdEnter".equals(shortcut) ? "cmdEnter" : "enter";
        PropertiesComponent.getInstance().setValue(SEND_SHORTCUT_PROPERTY_KEY, normalized);
        handleGetSendShortcut();
    }

    void handleGetAutoOpenFileEnabled() {
        try {
            String projectPath = context.getProject().getBasePath();
            boolean enabled = projectPath != null && settingsService.getAutoOpenFileEnabled(projectPath);
            JsonObject payload = new JsonObject();
            payload.addProperty("autoOpenFileEnabled", enabled);
            pushJson("window.updateAutoOpenFileEnabled", payload);
        } catch (Exception e) {
            showError("Failed to load auto open file config: " + e.getMessage());
        }
    }

    void handleSetAutoOpenFileEnabled(String content) {
        mutateProjectBoolean(content, "autoOpenFileEnabled", false, settingsService::setAutoOpenFileEnabled,
            "window.updateAutoOpenFileEnabled");
    }

    void handleGetPermissionDialogTimeout() {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("permissionDialogTimeoutSeconds", settingsService.getPermissionDialogTimeoutSeconds());
            pushJson("window.updatePermissionDialogTimeout", payload);
        } catch (Exception e) {
            showError("Failed to load permission dialog timeout: " + e.getMessage());
        }
    }

    void handleSetPermissionDialogTimeout(String content) {
        try {
            JsonObject json = gson.fromJson(content, JsonObject.class);
            int seconds = json != null && json.has("permissionDialogTimeoutSeconds")
                ? json.get("permissionDialogTimeoutSeconds").getAsInt()
                : CodemossSettingsService.DEFAULT_PERMISSION_DIALOG_TIMEOUT_SECONDS;
            settingsService.setPermissionDialogTimeoutSeconds(seconds);
            handleGetPermissionDialogTimeout();
        } catch (Exception e) {
            showError("Failed to save permission dialog timeout: " + e.getMessage());
        }
    }

    void handleGetCommitGenerationEnabled() {
        getBoolean("window.updateCommitGenerationEnabled", "commitGenerationEnabled",
            () -> settingsService.getCommitGenerationEnabled());
    }

    void handleSetCommitGenerationEnabled(String content) {
        setBoolean(content, "commitGenerationEnabled", settingsService::setCommitGenerationEnabled,
            "window.updateCommitGenerationEnabled");
    }

    void handleGetStatusBarWidgetEnabled() {
        getBoolean("window.updateStatusBarWidgetEnabled", "statusBarWidgetEnabled",
            () -> settingsService.getStatusBarWidgetEnabled());
    }

    void handleSetStatusBarWidgetEnabled(String content) {
        setBoolean(content, "statusBarWidgetEnabled", settingsService::setStatusBarWidgetEnabled,
            "window.updateStatusBarWidgetEnabled");
    }

    void handleGetTaskCompletionNotificationEnabled() {
        getBoolean("window.updateTaskCompletionNotificationEnabled", "taskCompletionNotificationEnabled",
            () -> settingsService.getTaskCompletionNotificationEnabled());
    }

    void handleSetTaskCompletionNotificationEnabled(String content) {
        setBoolean(content, "taskCompletionNotificationEnabled", settingsService::setTaskCompletionNotificationEnabled,
            "window.updateTaskCompletionNotificationEnabled");
    }

    void handleGetAiTitleGenerationEnabled() {
        getBoolean("window.updateAiTitleGenerationEnabled", "aiTitleGenerationEnabled",
            () -> settingsService.getAiTitleGenerationEnabled());
    }

    void handleSetAiTitleGenerationEnabled(String content) {
        setBoolean(content, "aiTitleGenerationEnabled", settingsService::setAiTitleGenerationEnabled,
            "window.updateAiTitleGenerationEnabled");
    }

    void handleGetIdeTheme() {
        ApplicationManager.getApplication().invokeLater(() ->
            context.callJavaScript("window.onIdeThemeReceived", context.escapeJs(ThemeConfigService.getIdeThemeConfigJson())));
    }

    void handleGetCommitPrompt() {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("commitPrompt", settingsService.getCommitPrompt());
            payload.addProperty("projectCommitPrompt", settingsService.getProjectCommitPrompt(context.getProject().getBasePath()));
            pushJson("window.updateCommitPrompt", payload);
        } catch (Exception e) {
            showError("Failed to load commit prompt: " + e.getMessage());
        }
    }

    void handleSetCommitPrompt(String content) {
        try {
            JsonObject json = gson.fromJson(content, JsonObject.class);
            settingsService.setCommitPrompt(readString(json, "prompt", ""));
            JsonObject payload = new JsonObject();
            payload.addProperty("commitPrompt", settingsService.getCommitPrompt());
            payload.addProperty("saved", true);
            pushJson("window.updateCommitPrompt", payload);
        } catch (Exception e) {
            showError("Failed to save commit prompt: " + e.getMessage());
        }
    }

    void handleGetCommitAiConfig() {
        try {
            pushJson("window.updateCommitAiConfig", settingsService.getCommitAiConfig());
        } catch (Exception e) {
            showError("Failed to load commit AI config: " + e.getMessage());
        }
    }

    void handleSetCommitAiConfig(String content) {
        try {
            settingsService.setCommitAiConfig(gson.fromJson(content, JsonObject.class));
            handleGetCommitAiConfig();
        } catch (Exception e) {
            showError("Failed to save commit AI config: " + e.getMessage());
        }
    }

    void handleGetPromptEnhancerConfig() {
        try {
            pushJson("window.updatePromptEnhancerConfig", settingsService.getPromptEnhancerConfig());
        } catch (Exception e) {
            showError("Failed to load prompt enhancer config: " + e.getMessage());
        }
    }

    void handleSetPromptEnhancerConfig(String content) {
        try {
            settingsService.setPromptEnhancerConfig(gson.fromJson(content, JsonObject.class));
            handleGetPromptEnhancerConfig();
        } catch (Exception e) {
            showError("Failed to save prompt enhancer config: " + e.getMessage());
        }
    }

    void handleGetProjectCommitPrompt() {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("projectCommitPrompt", settingsService.getProjectCommitPrompt(context.getProject().getBasePath()));
            pushJson("window.updateProjectCommitPrompt", payload);
        } catch (Exception e) {
            showError("Failed to load project commit prompt: " + e.getMessage());
        }
    }

    void handleSetProjectCommitPrompt(String content) {
        try {
            JsonObject json = gson.fromJson(content, JsonObject.class);
            settingsService.setProjectCommitPrompt(context.getProject().getBasePath(), readString(json, "prompt", ""));
            JsonObject payload = new JsonObject();
            payload.addProperty("projectCommitPrompt", settingsService.getProjectCommitPrompt(context.getProject().getBasePath()));
            payload.addProperty("saved", true);
            pushJson("window.updateProjectCommitPrompt", payload);
        } catch (Exception e) {
            showError("Failed to save project commit prompt: " + e.getMessage());
        }
    }

    private JsonObject enrichFontConfig(JsonObject stored, boolean codeFont) {
        JsonObject payload = stored.deepCopy();
        String customPath = readString(stored, "customFontPath", "");
        String editorFamily = EditorColorsManager.getInstance().getGlobalScheme().getEditorFontName();
        int editorSize = EditorColorsManager.getInstance().getGlobalScheme().getEditorFontSize();
        float lineSpacing = EditorColorsManager.getInstance().getGlobalScheme().getLineSpacing();
        if ("customFile".equals(readString(stored, "effectiveMode", readString(stored, "mode", "followEditor")))) {
            File file = new File(customPath);
            if (file.exists() && file.isFile()) {
                payload.addProperty("fontFamily", file.getName().replaceFirst("\\.[^.]+$", ""));
                payload.addProperty("displayName", file.getName());
                payload.addProperty("fontSize", editorSize);
                payload.addProperty("lineSpacing", lineSpacing);
                addFontPayload(payload, file);
                return payload;
            }
            payload.addProperty("effectiveMode", "followEditor");
            payload.addProperty("warningCode", "fontUnavailable");
            payload.addProperty("warning", "Custom font file is unavailable");
        }
        java.awt.Font font = UIManager.getFont("Label.font");
        payload.addProperty("fontFamily", codeFont ? editorFamily : (font != null ? font.getFamily() : editorFamily));
        payload.addProperty("displayName", codeFont ? editorFamily : (font != null ? font.getFamily() : editorFamily));
        payload.addProperty("fontSize", codeFont ? editorSize : (font != null ? font.getSize() : editorSize));
        payload.addProperty("lineSpacing", lineSpacing);
        return payload;
    }

    private void addFontPayload(JsonObject payload, File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String ext = file.getName().toLowerCase().endsWith(".otf") ? "opentype" : "truetype";
            payload.addProperty("fontBase64", Base64.getEncoder().encodeToString(bytes));
            payload.addProperty("fontFormat", ext);
        } catch (Exception e) {
            LOG.warn("[ProjectConfigHandler] Failed to inline font bytes: " + e.getMessage());
        }
    }

    private void browseFontFile(FontPathConsumer consumer) {
        ApplicationManager.getApplication().invokeLater(() -> {
            FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false);
            descriptor.withTitle("Select Font File");
            descriptor.withFileFilter(file -> {
                String ext = file.getExtension();
                return ext != null && (ext.equalsIgnoreCase("ttf") || ext.equalsIgnoreCase("otf"));
            });
            FileChooser.chooseFile(descriptor, context.getProject(), null, file -> {
                if (file != null) {
                    consumer.accept(file.getPath());
                }
            });
        });
    }

    private JsonObject zeroUsage() {
        JsonObject usage = new JsonObject();
        usage.addProperty("inputTokens", 0);
        usage.addProperty("outputTokens", 0);
        usage.addProperty("cacheWriteTokens", 0);
        usage.addProperty("cacheReadTokens", 0);
        usage.addProperty("totalTokens", 0);
        return usage;
    }

    private JsonObject weekBlock() {
        JsonObject block = new JsonObject();
        block.addProperty("sessions", 0);
        block.addProperty("cost", 0);
        block.addProperty("tokens", 0);
        return block;
    }

    private void getBoolean(String callback, String key, ThrowingBooleanSupplier supplier) {
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty(key, supplier.getAsBoolean());
            pushJson(callback, payload);
        } catch (Exception e) {
            showError("Failed to load setting: " + e.getMessage());
        }
    }

    private void setBoolean(String content, String key, ThrowingBooleanConsumer consumer, String callback) {
        try {
            JsonObject json = gson.fromJson(content, JsonObject.class);
            boolean value = json != null && json.has(key) && !json.get(key).isJsonNull() && json.get(key).getAsBoolean();
            consumer.accept(value);
            JsonObject payload = new JsonObject();
            payload.addProperty(key, value);
            pushJson(callback, payload);
        } catch (Exception e) {
            showError("Failed to save setting: " + e.getMessage());
        }
    }

    private void mutateProjectBoolean(String content, String key, boolean defaultValue,
                                      ThrowingProjectBooleanConsumer consumer, String callback) {
        try {
            String projectPath = context.getProject().getBasePath();
            if (projectPath == null) {
                showError("Unable to resolve project path");
                return;
            }
            JsonObject json = gson.fromJson(content, JsonObject.class);
            boolean value = json != null && json.has(key) && !json.get(key).isJsonNull()
                ? json.get(key).getAsBoolean()
                : defaultValue;
            consumer.accept(projectPath, value);
            JsonObject payload = new JsonObject();
            payload.addProperty(key, value);
            pushJson(callback, payload);
        } catch (Exception e) {
            showError("Failed to save setting: " + e.getMessage());
        }
    }

    private String readString(JsonObject json, String key, String fallback) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return fallback;
        }
        return json.get(key).getAsString();
    }

    private void pushJson(String callback, JsonObject payload) {
        ApplicationManager.getApplication().invokeLater(() ->
            context.callJavaScript(callback, context.escapeJs(payload.toString())));
    }

    private void showError(String message) {
        ApplicationManager.getApplication().invokeLater(() ->
            context.callJavaScript("window.showError", context.escapeJs(message)));
    }

    private void showSuccess(String message) {
        ApplicationManager.getApplication().invokeLater(() ->
            context.callJavaScript("window.showSuccess", context.escapeJs(message)));
    }

    private void showSuccessI18n(String key) {
        ApplicationManager.getApplication().invokeLater(() ->
            context.callJavaScript("window.showSuccessI18n", context.escapeJs(key)));
    }

    @FunctionalInterface
    private interface ThrowingProjectBooleanConsumer {
        void accept(String projectPath, boolean value) throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingBooleanSupplier {
        boolean getAsBoolean() throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingBooleanConsumer {
        void accept(boolean value) throws Exception;
    }

    @FunctionalInterface
    private interface FontPathConsumer {
        void accept(String path);
    }
}
