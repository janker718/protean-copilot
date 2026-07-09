package com.protean.copilot.handler;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.protean.copilot.handler.core.BaseMessageHandler;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.handler.provider.ModelProviderHandler;
import com.protean.copilot.settings.CodemossSettingsService;
import com.protean.copilot.util.LanguageConfigService;
import com.protean.copilot.util.ThemeConfigService;

import java.util.List;

public final class SettingsHandler extends BaseMessageHandler {

    private static final Logger LOG = Logger.getInstance(SettingsHandler.class);

    private final Gson gson = new Gson();
    private final CodemossSettingsService codemossSettingsService = new CodemossSettingsService();
    private final InputHistoryHandler inputHistoryHandler;
    private final SoundSettingsHandler soundSettingsHandler;
    private final UsagePushService usagePushService;
    private final PermissionModeHandler permissionModeHandler;
    private final ModelProviderHandler modelProviderHandler;
    private final NodePathHandler nodePathHandler;
    private final ClaudeCliPathHandler claudeCliPathHandler;
    private final ProjectConfigHandler projectConfigHandler;
    private final CodexSubscriptionQuotaHandler codexSubscriptionQuotaHandler;

    private static final List<String> SUPPORTED_TYPES = List.of(
        "get_mode",
        "set_mode",
        "set_model",
        "set_provider",
        "set_reasoning_effort",
        "set_codex_fast_mode",
        "get_node_path",
        "set_node_path",
        "get_claude_cli_path",
        "set_claude_cli_path",
        "get_usage_statistics",
        "get_codex_subscription_quota",
        "get_working_directory",
        "set_working_directory",
        "get_editor_font_config",
        "get_ui_font_config",
        "set_ui_font_config",
        "browse_ui_font_file",
        "get_code_font_config",
        "set_code_font_config",
        "browse_code_font_file",
        "get_streaming_enabled",
        "set_streaming_enabled",
        "get_codex_sandbox_mode",
        "set_codex_sandbox_mode",
        "get_send_shortcut",
        "set_send_shortcut",
        "get_auto_open_file_enabled",
        "set_auto_open_file_enabled",
        "get_permission_dialog_timeout",
        "set_permission_dialog_timeout",
        "get_commit_generation_enabled",
        "set_commit_generation_enabled",
        "get_status_bar_widget_enabled",
        "set_status_bar_widget_enabled",
        "get_task_completion_notification_enabled",
        "set_task_completion_notification_enabled",
        "get_ai_title_generation_enabled",
        "set_ai_title_generation_enabled",
        "get_ide_theme",
        "get_commit_prompt",
        "set_commit_prompt",
        "get_commit_ai_config",
        "set_commit_ai_config",
        "get_prompt_enhancer_config",
        "set_prompt_enhancer_config",
        "get_project_commit_prompt",
        "set_project_commit_prompt",
        "get_input_history",
        "record_input_history",
        "delete_input_history_item",
        "clear_input_history",
        "get_sound_notification_config",
        "set_sound_notification_enabled",
        "set_sound_only_when_unfocused",
        "set_selected_sound",
        "set_custom_sound_path",
        "test_sound",
        "browse_sound_file",
        "set_user_language",
        "get_user_language",
        "clear_user_language"
    );

    public SettingsHandler(HandlerContext context) {
        super(context);
        this.inputHistoryHandler = new InputHistoryHandler(context);
        this.soundSettingsHandler = new SoundSettingsHandler(context);
        this.usagePushService = new UsagePushService(context);
        this.permissionModeHandler = new PermissionModeHandler(context);
        this.modelProviderHandler = new ModelProviderHandler(context, usagePushService);
        this.nodePathHandler = new NodePathHandler(context);
        this.claudeCliPathHandler = new ClaudeCliPathHandler(context);
        this.projectConfigHandler = new ProjectConfigHandler(context);
        this.codexSubscriptionQuotaHandler = new CodexSubscriptionQuotaHandler(context);
        registerThemeChangeListener();
    }

    @Override
    public boolean handle(String type, String content) {
        switch (type) {
            case "get_mode" -> permissionModeHandler.handleGetMode();
            case "set_mode" -> permissionModeHandler.handleSetMode(content);
            case "set_model" -> modelProviderHandler.handleSetModel(content);
            case "set_provider" -> modelProviderHandler.handleSetProvider(content);
            case "set_reasoning_effort" -> modelProviderHandler.handleSetReasoningEffort(content);
            case "set_codex_fast_mode" -> modelProviderHandler.handleSetCodexFastMode(content);
            case "get_node_path" -> nodePathHandler.handleGetNodePath();
            case "set_node_path" -> nodePathHandler.handleSetNodePath(content);
            case "get_claude_cli_path" -> claudeCliPathHandler.handleGetClaudeCliPath();
            case "set_claude_cli_path" -> claudeCliPathHandler.handleSetClaudeCliPath(content);
            case "get_usage_statistics" -> projectConfigHandler.handleGetUsageStatistics(content);
            case "get_codex_subscription_quota" -> codexSubscriptionQuotaHandler.handleGetCodexSubscriptionQuota();
            case "get_working_directory" -> projectConfigHandler.handleGetWorkingDirectory();
            case "set_working_directory" -> projectConfigHandler.handleSetWorkingDirectory(content);
            case "get_editor_font_config" -> projectConfigHandler.handleGetEditorFontConfig();
            case "get_ui_font_config" -> projectConfigHandler.handleGetUiFontConfig();
            case "set_ui_font_config" -> projectConfigHandler.handleSetUiFontConfig(content);
            case "browse_ui_font_file" -> projectConfigHandler.handleBrowseUiFontFile();
            case "get_code_font_config" -> projectConfigHandler.handleGetCodeFontConfig();
            case "set_code_font_config" -> projectConfigHandler.handleSetCodeFontConfig(content);
            case "browse_code_font_file" -> projectConfigHandler.handleBrowseCodeFontFile();
            case "get_streaming_enabled" -> projectConfigHandler.handleGetStreamingEnabled();
            case "set_streaming_enabled" -> projectConfigHandler.handleSetStreamingEnabled(content);
            case "get_codex_sandbox_mode" -> projectConfigHandler.handleGetCodexSandboxMode();
            case "set_codex_sandbox_mode" -> projectConfigHandler.handleSetCodexSandboxMode(content);
            case "get_send_shortcut" -> projectConfigHandler.handleGetSendShortcut();
            case "set_send_shortcut" -> projectConfigHandler.handleSetSendShortcut(content);
            case "get_auto_open_file_enabled" -> projectConfigHandler.handleGetAutoOpenFileEnabled();
            case "set_auto_open_file_enabled" -> projectConfigHandler.handleSetAutoOpenFileEnabled(content);
            case "get_permission_dialog_timeout" -> projectConfigHandler.handleGetPermissionDialogTimeout();
            case "set_permission_dialog_timeout" -> projectConfigHandler.handleSetPermissionDialogTimeout(content);
            case "get_commit_generation_enabled" -> projectConfigHandler.handleGetCommitGenerationEnabled();
            case "set_commit_generation_enabled" -> projectConfigHandler.handleSetCommitGenerationEnabled(content);
            case "get_status_bar_widget_enabled" -> projectConfigHandler.handleGetStatusBarWidgetEnabled();
            case "set_status_bar_widget_enabled" -> projectConfigHandler.handleSetStatusBarWidgetEnabled(content);
            case "get_task_completion_notification_enabled" -> projectConfigHandler.handleGetTaskCompletionNotificationEnabled();
            case "set_task_completion_notification_enabled" -> projectConfigHandler.handleSetTaskCompletionNotificationEnabled(content);
            case "get_ai_title_generation_enabled" -> projectConfigHandler.handleGetAiTitleGenerationEnabled();
            case "set_ai_title_generation_enabled" -> projectConfigHandler.handleSetAiTitleGenerationEnabled(content);
            case "get_ide_theme" -> projectConfigHandler.handleGetIdeTheme();
            case "get_commit_prompt" -> projectConfigHandler.handleGetCommitPrompt();
            case "set_commit_prompt" -> projectConfigHandler.handleSetCommitPrompt(content);
            case "get_commit_ai_config" -> projectConfigHandler.handleGetCommitAiConfig();
            case "set_commit_ai_config" -> projectConfigHandler.handleSetCommitAiConfig(content);
            case "get_prompt_enhancer_config" -> projectConfigHandler.handleGetPromptEnhancerConfig();
            case "set_prompt_enhancer_config" -> projectConfigHandler.handleSetPromptEnhancerConfig(content);
            case "get_project_commit_prompt" -> projectConfigHandler.handleGetProjectCommitPrompt();
            case "set_project_commit_prompt" -> projectConfigHandler.handleSetProjectCommitPrompt(content);
            case "get_input_history" -> inputHistoryHandler.handleGetInputHistory();
            case "record_input_history" -> inputHistoryHandler.handleRecordInputHistory(content);
            case "delete_input_history_item" -> inputHistoryHandler.handleDeleteInputHistoryItem(content);
            case "clear_input_history" -> inputHistoryHandler.handleClearInputHistory();
            case "get_sound_notification_config" -> soundSettingsHandler.handleGetSoundNotificationConfig();
            case "set_sound_notification_enabled" -> soundSettingsHandler.handleSetSoundNotificationEnabled(content);
            case "set_sound_only_when_unfocused" -> soundSettingsHandler.handleSetSoundOnlyWhenUnfocused(content);
            case "set_selected_sound" -> soundSettingsHandler.handleSetSelectedSound(content);
            case "set_custom_sound_path" -> soundSettingsHandler.handleSetCustomSoundPath(content);
            case "test_sound" -> soundSettingsHandler.handleTestSound(content);
            case "browse_sound_file" -> soundSettingsHandler.handleBrowseSoundFile();
            case "set_user_language" -> handleSetUserLanguage(content);
            case "get_user_language" -> handleGetUserLanguage();
            case "clear_user_language" -> handleClearUserLanguage();
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<String> getSupportedTypes() {
        return SUPPORTED_TYPES;
    }

    private void registerThemeChangeListener() {
        ThemeConfigService.registerThemeChangeListener(themeConfig ->
            ApplicationManager.getApplication().invokeLater(() ->
                callJavaScript("window.onIdeThemeChanged", escapeJs(themeConfig.toString()))));
    }

    private void handleSetUserLanguage(String content) {
        try {
            JsonObject json = gson.fromJson(content, JsonObject.class);
            String language = json != null && json.has("language") && !json.get("language").isJsonNull()
                ? json.get("language").getAsString()
                : null;
            if (language == null || language.isBlank()) {
                pushLanguageConfig();
                return;
            }
            LanguageConfigService.setUserLanguage(codemossSettingsService, language);
            pushLanguageConfig();
        } catch (Exception e) {
            LOG.warn("[SettingsHandler] Failed to set user language: " + e.getMessage(), e);
            pushLanguageConfig();
        }
    }

    private void handleGetUserLanguage() {
        String language = LanguageConfigService.getUserLanguage(codemossSettingsService);
        JsonObject payload = new JsonObject();
        payload.addProperty("language", language != null ? language : "");
        payload.addProperty("manuallySet", language != null);
        callJavaScript("window.onUserLanguage", escapeJs(payload.toString()));
    }

    private void handleClearUserLanguage() {
        try {
            LanguageConfigService.clearUserLanguage(codemossSettingsService);
        } catch (Exception e) {
            LOG.warn("[SettingsHandler] Failed to clear user language: " + e.getMessage(), e);
        } finally {
            pushLanguageConfig();
        }
    }

    private void pushLanguageConfig() {
        JsonObject config = LanguageConfigService.getLanguageConfig(codemossSettingsService);
        callJavaScript("window.applyIdeaLanguageConfig", escapeJs(config.toString()));
    }

    public static int getModelContextLimit(String model) {
        return ModelProviderHandler.getModelContextLimit(model);
    }
}
