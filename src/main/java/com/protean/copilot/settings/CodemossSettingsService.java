package com.protean.copilot.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 轻量版 Codemoss 配置服务。
 *
 * <p>结构和方法命名尽量对齐参考仓库的 {@code CodemossSettingsService}，
 * 但当前仓库缺少大量 manager / model 依赖，因此这里提供一个可落地的 facade：
 * 一部分能力委托给现有 {@link SettingsService}，
 * 另一部分使用 {@code ~/.codemoss/config.json} 做持久化。</p>
 */
public class CodemossSettingsService {

    private static final Logger LOG = Logger.getInstance(CodemossSettingsService.class);

    private static final int CONFIG_VERSION = 1;
    public static final String CODEX_RUNTIME_ACCESS_INACTIVE = "inactive";
    public static final String CODEX_RUNTIME_ACCESS_MANAGED = "managed";
    public static final String CODEX_RUNTIME_ACCESS_CLI_LOGIN = "cli_login";

    private static final String KEY_LANGUAGE = "language";
    private static final String KEY_STREAMING = "streaming";
    private static final String KEY_AUTO_OPEN_FILE = "autoOpenFile";
    private static final String KEY_AGENTS = "agents";
    private static final String KEY_SELECTED_AGENT_ID = "selectedAgentId";
    private static final String KEY_COMMIT_PROMPT = "commitPrompt";
    private static final String KEY_PROJECT_COMMIT_PROMPT = "projectCommitPrompt";
    private static final String KEY_STATUS_BAR_WIDGET_ENABLED = "statusBarWidgetEnabled";
    private static final String KEY_TASK_COMPLETION_NOTIFICATION_ENABLED = "taskCompletionNotificationEnabled";
    private static final String KEY_COMMIT_GENERATION_ENABLED = "commitGenerationEnabled";
    private static final String KEY_AI_TITLE_GENERATION_ENABLED = "aiTitleGenerationEnabled";
    private static final String KEY_SOUND_NOTIFICATION = "soundNotification";
    private static final String KEY_SOUND_ENABLED = "enabled";
    private static final String KEY_SOUND_ONLY_WHEN_UNFOCUSED = "onlyWhenUnfocused";
    private static final String KEY_SOUND_SELECTED = "selectedSound";
    private static final String KEY_SOUND_CUSTOM_PATH = "customSoundPath";
    private static final String KEY_SEND_SHORTCUT = "sendShortcut";
    private static final String KEY_UI_FONT_CONFIG = "uiFontConfig";
    private static final String KEY_CODE_FONT_CONFIG = "codeFontConfig";
    private static final String KEY_MODE = "mode";
    private static final String KEY_CUSTOM_FONT_PATH = "customFontPath";
    private static final String KEY_COMMIT_AI_CONFIG = "commitAiConfig";
    private static final String KEY_PROMPT_ENHANCER_CONFIG = "promptEnhancerConfig";
    private static final String KEY_CODEX = "codex";
    private static final String KEY_CODEX_CURRENT = "current";
    private static final String KEY_CODEX_PROVIDERS = "providers";
    private static final String KEY_CODEX_LOCAL_CONFIG_AUTHORIZED = "localConfigAuthorized";
    private static final String KEY_CODEX_SANDBOX_MODE = "sandboxMode";

    private static final String DEFAULT_CONFIG_DIR_NAME = ".codemoss";
    private static final String DEFAULT_CONFIG_FILE_NAME = "config.json";
    private static final String DEFAULT_BACKUP_FILE_NAME = "config.json.bak";
    private static final String DEFAULT_CODEX_CLI_LOGIN_PROVIDER_ID = "codex-cli-login";
    public static final int DEFAULT_PERMISSION_DIALOG_TIMEOUT_SECONDS =
        PermissionDialogTimeoutSettings.DEFAULT_PERMISSION_DIALOG_TIMEOUT_SECONDS;
    public static final long PERMISSION_SAFETY_NET_BUFFER_SECONDS =
        PermissionDialogTimeoutSettings.PERMISSION_SAFETY_NET_BUFFER_SECONDS;

    private final Gson gson;
    private final SettingsService settingsService;

    public CodemossSettingsService() {
        this.gson = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
        this.settingsService = new SettingsService();
    }

    // ==================== Basic Config Management ====================

    public String getConfigPath() {
        return getConfigFilePath().toString();
    }

    public JsonObject readConfig() throws IOException {
        Path configPath = getConfigFilePath();
        if (!Files.exists(configPath)) {
            LOG.info("[CodemossSettings] Config file not found, creating default: " + configPath);
            JsonObject config = createDefaultConfig();
            writeConfig(config);
            return config;
        }

        try (FileReader reader = new FileReader(configPath.toFile(), StandardCharsets.UTF_8)) {
            JsonObject config = JsonParser.parseReader(reader).getAsJsonObject();
            LOG.info("[CodemossSettings] Successfully read config from: " + configPath);
            ensureDefaultSections(config);
            return config;
        } catch (Exception e) {
            LOG.warn("[CodemossSettings] Failed to read config, fallback to default: " + e.getMessage());
            JsonObject config = createDefaultConfig();
            writeConfig(config);
            return config;
        }
    }

    public void writeConfig(JsonObject config) throws IOException {
        ensureConfigDirectory();
        backupConfig();
        ensureDefaultSections(config);

        Path configPath = getConfigFilePath();
        try (FileWriter writer = new FileWriter(configPath.toFile(), StandardCharsets.UTF_8)) {
            gson.toJson(config, writer);
            LOG.info("[CodemossSettings] Successfully wrote config to: " + configPath);
        }
    }

    // ==================== Existing SettingsService Compatibility ====================

    public String getPermissionMode() {
        return settingsService.getPermissionMode();
    }

    public void setPermissionMode(String mode) {
        settingsService.setPermissionMode(mode);
    }

    public String getProvider() {
        return settingsService.getProvider();
    }

    public void setProvider(String provider) {
        settingsService.setProvider(provider);
    }

    public String getNodePath() {
        return settingsService.getNodePath();
    }

    public void setNodePath(String path) {
        settingsService.setNodePath(path);
    }

    public @Nullable String getBridgeScriptPath() {
        return settingsService.getBridgeScriptPath();
    }

    public void setBridgeScriptPath(@Nullable String path) {
        settingsService.setBridgeScriptPath(path);
    }

    public @Nullable String getCustomWorkingDirectory(String projectPath) throws IOException {
        JsonObject config = readConfig();
        if (config.has("workingDirectories") && config.get("workingDirectories").isJsonObject()) {
            JsonObject directories = config.getAsJsonObject("workingDirectories");
            if (projectPath != null && directories.has(projectPath)) {
                return getNullableString(directories, projectPath);
            }
        }
        return settingsService.getCustomWorkingDirectory(projectPath);
    }

    public void setCustomWorkingDirectory(String projectPath, @Nullable String customWorkingDir) throws IOException {
        JsonObject config = readConfig();
        JsonObject directories = ensureObject(config, "workingDirectories");
        if (projectPath != null) {
            if (customWorkingDir == null || customWorkingDir.isBlank()) {
                directories.remove(projectPath);
            } else {
                directories.addProperty(projectPath, customWorkingDir);
            }
        }
        writeConfig(config);
        settingsService.setCustomWorkingDirectory(projectPath, customWorkingDir);
    }

    public Map<String, String> getAllWorkingDirectories() throws IOException {
        JsonObject config = readConfig();
        JsonObject directories = config.has("workingDirectories") && config.get("workingDirectories").isJsonObject()
            ? config.getAsJsonObject("workingDirectories")
            : new JsonObject();
        java.util.LinkedHashMap<String, String> result = new java.util.LinkedHashMap<>();
        for (String key : directories.keySet()) {
            if (!directories.get(key).isJsonNull()) {
                result.put(key, directories.get(key).getAsString());
            }
        }
        return result;
    }

    // ==================== Language Config Management ====================

    public String getUserLanguage() throws IOException {
        JsonObject config = readConfig();
        return getNullableString(config, KEY_LANGUAGE);
    }

    public void setUserLanguage(String language) throws IOException {
        JsonObject config = readConfig();
        config.addProperty(KEY_LANGUAGE, language);
        writeConfig(config);
    }

    public void clearUserLanguage() throws IOException {
        JsonObject config = readConfig();
        config.remove(KEY_LANGUAGE);
        writeConfig(config);
    }

    // ==================== Streaming Config Management ====================

    public boolean getStreamingEnabled(String projectPath) throws IOException {
        return readProjectBooleanConfig(KEY_STREAMING, projectPath, true);
    }

    public void setStreamingEnabled(String projectPath, boolean enabled) throws IOException {
        writeProjectBooleanConfig(KEY_STREAMING, projectPath, enabled);
    }

    // ==================== Auto Open File Config Management ====================

    public boolean getAutoOpenFileEnabled(String projectPath) throws IOException {
        return readProjectBooleanConfig(KEY_AUTO_OPEN_FILE, projectPath, false);
    }

    public void setAutoOpenFileEnabled(String projectPath, boolean enabled) throws IOException {
        writeProjectBooleanConfig(KEY_AUTO_OPEN_FILE, projectPath, enabled);
    }

    public int getPermissionDialogTimeoutSeconds() throws IOException {
        return PermissionDialogTimeoutSettings.getPermissionDialogTimeoutSeconds(this);
    }

    public void setPermissionDialogTimeoutSeconds(int seconds) throws IOException {
        PermissionDialogTimeoutSettings.setPermissionDialogTimeoutSeconds(this, seconds);
    }

    // ==================== Commit Prompt Config Management ====================

    public String getCommitPrompt() throws IOException {
        JsonObject config = readConfig();
        String prompt = getNullableString(config, KEY_COMMIT_PROMPT);
        return prompt != null ? prompt : "Generate a concise conventional commit message for the current changes.";
    }

    public void setCommitPrompt(String prompt) throws IOException {
        JsonObject config = readConfig();
        config.addProperty(KEY_COMMIT_PROMPT, prompt);
        writeConfig(config);
    }

    public String getProjectCommitPrompt(String projectPath) throws IOException {
        if (projectPath == null) {
            return "";
        }
        JsonObject config = readConfig();
        if (config.has(KEY_PROJECT_COMMIT_PROMPT) && config.get(KEY_PROJECT_COMMIT_PROMPT).isJsonObject()) {
            JsonObject prompts = config.getAsJsonObject(KEY_PROJECT_COMMIT_PROMPT);
            if (prompts.has(projectPath) && !prompts.get(projectPath).isJsonNull()) {
                return prompts.get(projectPath).getAsString();
            }
        }
        return "";
    }

    public void setProjectCommitPrompt(String projectPath, String prompt) throws IOException {
        if (projectPath == null) {
            return;
        }
        JsonObject config = readConfig();
        JsonObject prompts = ensureObject(config, KEY_PROJECT_COMMIT_PROMPT);
        prompts.addProperty(projectPath, prompt);
        writeConfig(config);
    }

    // ==================== Agent Management ====================

    public List<JsonObject> getAgents() throws IOException {
        JsonObject config = readConfig();
        JsonObject agents = ensureObject(config, KEY_AGENTS);
        List<JsonObject> result = new ArrayList<>();
        for (String key : agents.keySet()) {
            if (agents.get(key).isJsonObject()) {
                JsonObject agent = agents.getAsJsonObject(key).deepCopy();
                if (!agent.has("id")) {
                    agent.addProperty("id", key);
                }
                result.add(agent);
            }
        }
        return result;
    }

    public void addAgent(JsonObject agent) throws IOException {
        if (agent == null) {
            return;
        }
        String id = extractAgentId(agent);
        JsonObject config = readConfig();
        JsonObject agents = ensureObject(config, KEY_AGENTS);
        JsonObject normalized = agent.deepCopy();
        normalized.addProperty("id", id);
        agents.add(id, normalized);
        writeConfig(config);
    }

    public void updateAgent(String id, JsonObject updates) throws IOException {
        if (id == null || id.isBlank() || updates == null) {
            return;
        }
        JsonObject config = readConfig();
        JsonObject agents = ensureObject(config, KEY_AGENTS);
        JsonObject current = agents.has(id) && agents.get(id).isJsonObject()
            ? agents.getAsJsonObject(id).deepCopy()
            : new JsonObject();
        for (String key : updates.keySet()) {
            current.add(key, updates.get(key));
        }
        current.addProperty("id", id);
        agents.add(id, current);
        writeConfig(config);
    }

    public boolean deleteAgent(String id) throws IOException {
        if (id == null || id.isBlank()) {
            return false;
        }
        JsonObject config = readConfig();
        JsonObject agents = ensureObject(config, KEY_AGENTS);
        boolean existed = agents.has(id);
        agents.remove(id);

        String selected = getNullableString(config, KEY_SELECTED_AGENT_ID);
        if (id.equals(selected)) {
            config.add(KEY_SELECTED_AGENT_ID, JsonNull.INSTANCE);
        }

        writeConfig(config);
        return existed;
    }

    public JsonObject getAgent(String id) throws IOException {
        if (id == null || id.isBlank()) {
            return null;
        }
        JsonObject config = readConfig();
        JsonObject agents = ensureObject(config, KEY_AGENTS);
        if (!agents.has(id) || !agents.get(id).isJsonObject()) {
            return null;
        }
        JsonObject agent = agents.getAsJsonObject(id).deepCopy();
        if (!agent.has("id")) {
            agent.addProperty("id", id);
        }
        return agent;
    }

    public String getSelectedAgentId() throws IOException {
        JsonObject config = readConfig();
        return getNullableString(config, KEY_SELECTED_AGENT_ID);
    }

    public void setSelectedAgentId(String agentId) throws IOException {
        JsonObject config = readConfig();
        if (agentId == null || agentId.isBlank()) {
            config.add(KEY_SELECTED_AGENT_ID, JsonNull.INSTANCE);
        } else {
            config.addProperty(KEY_SELECTED_AGENT_ID, agentId);
        }
        writeConfig(config);
    }

    // ==================== Basic Flags ====================

    public boolean getStatusBarWidgetEnabled() throws IOException {
        return readBooleanFlag(KEY_STATUS_BAR_WIDGET_ENABLED, true);
    }

    public void setStatusBarWidgetEnabled(boolean enabled) throws IOException {
        writeBooleanFlag(KEY_STATUS_BAR_WIDGET_ENABLED, enabled);
    }

    public boolean getTaskCompletionNotificationEnabled() throws IOException {
        return readBooleanFlag(KEY_TASK_COMPLETION_NOTIFICATION_ENABLED, false);
    }

    public void setTaskCompletionNotificationEnabled(boolean enabled) throws IOException {
        writeBooleanFlag(KEY_TASK_COMPLETION_NOTIFICATION_ENABLED, enabled);
    }

    public boolean getCommitGenerationEnabled() throws IOException {
        return readBooleanFlag(KEY_COMMIT_GENERATION_ENABLED, true);
    }

    public void setCommitGenerationEnabled(boolean enabled) throws IOException {
        writeBooleanFlag(KEY_COMMIT_GENERATION_ENABLED, enabled);
    }

    public boolean getAiTitleGenerationEnabled() throws IOException {
        return readBooleanFlag(KEY_AI_TITLE_GENERATION_ENABLED, true);
    }

    public void setAiTitleGenerationEnabled(boolean enabled) throws IOException {
        writeBooleanFlag(KEY_AI_TITLE_GENERATION_ENABLED, enabled);
    }

    public boolean getSoundNotificationEnabled() throws IOException {
        return readSoundConfig().has(KEY_SOUND_ENABLED)
            && readSoundConfig().get(KEY_SOUND_ENABLED).getAsBoolean();
    }

    public void setSoundNotificationEnabled(boolean enabled) throws IOException {
        JsonObject config = readConfig();
        JsonObject sound = ensureObject(config, KEY_SOUND_NOTIFICATION);
        sound.addProperty(KEY_SOUND_ENABLED, enabled);
        writeConfig(config);
    }

    public boolean getSoundOnlyWhenUnfocused() throws IOException {
        JsonObject sound = readSoundConfig();
        return sound.has(KEY_SOUND_ONLY_WHEN_UNFOCUSED)
            && !sound.get(KEY_SOUND_ONLY_WHEN_UNFOCUSED).isJsonNull()
            && sound.get(KEY_SOUND_ONLY_WHEN_UNFOCUSED).getAsBoolean();
    }

    public void setSoundOnlyWhenUnfocused(boolean onlyWhenUnfocused) throws IOException {
        JsonObject config = readConfig();
        JsonObject sound = ensureObject(config, KEY_SOUND_NOTIFICATION);
        sound.addProperty(KEY_SOUND_ONLY_WHEN_UNFOCUSED, onlyWhenUnfocused);
        writeConfig(config);
    }

    public String getSelectedSound() throws IOException {
        JsonObject sound = readSoundConfig();
        return getNullableString(sound, KEY_SOUND_SELECTED) != null
            ? getNullableString(sound, KEY_SOUND_SELECTED)
            : "default";
    }

    public void setSelectedSound(String soundId) throws IOException {
        JsonObject config = readConfig();
        JsonObject sound = ensureObject(config, KEY_SOUND_NOTIFICATION);
        sound.addProperty(KEY_SOUND_SELECTED, soundId == null || soundId.isBlank() ? "default" : soundId.trim());
        writeConfig(config);
    }

    public @Nullable String getCustomSoundPath() throws IOException {
        return getNullableString(readSoundConfig(), KEY_SOUND_CUSTOM_PATH);
    }

    public void setCustomSoundPath(@Nullable String path) throws IOException {
        JsonObject config = readConfig();
        JsonObject sound = ensureObject(config, KEY_SOUND_NOTIFICATION);
        if (path == null || path.isBlank()) {
            sound.remove(KEY_SOUND_CUSTOM_PATH);
        } else {
            sound.addProperty(KEY_SOUND_CUSTOM_PATH, path.trim());
        }
        writeConfig(config);
    }

    public String getSendShortcut() throws IOException {
        JsonObject config = readConfig();
        String shortcut = getNullableString(config, KEY_SEND_SHORTCUT);
        return "cmdEnter".equals(shortcut) ? "cmdEnter" : "enter";
    }

    public void setSendShortcut(String shortcut) throws IOException {
        JsonObject config = readConfig();
        config.addProperty(KEY_SEND_SHORTCUT, "cmdEnter".equals(shortcut) ? "cmdEnter" : "enter");
        writeConfig(config);
    }

    public String getCodexSandboxMode(@Nullable String projectPath) throws IOException {
        JsonObject config = readConfig();
        JsonObject codex = ensureObject(config, KEY_CODEX);
        JsonObject sandbox = ensureObject(codex, KEY_CODEX_SANDBOX_MODE);
        if (projectPath != null && sandbox.has(projectPath) && !sandbox.get(projectPath).isJsonNull()) {
            return sandbox.get(projectPath).getAsString();
        }
        String fallback = getNullableString(sandbox, "default");
        return isValidSandboxMode(fallback) ? fallback : "danger-full-access";
    }

    public void setCodexSandboxMode(@Nullable String projectPath, @Nullable String sandboxMode) throws IOException {
        JsonObject config = readConfig();
        JsonObject codex = ensureObject(config, KEY_CODEX);
        JsonObject sandbox = ensureObject(codex, KEY_CODEX_SANDBOX_MODE);
        String normalized = isValidSandboxMode(sandboxMode) ? sandboxMode : "danger-full-access";
        sandbox.addProperty("default", normalized);
        if (projectPath != null && !projectPath.isBlank()) {
            sandbox.addProperty(projectPath, normalized);
        }
        writeConfig(config);
    }

    public JsonObject getUiFontConfig() throws IOException {
        return normalizeFontConfig(readFontConfig(KEY_UI_FONT_CONFIG));
    }

    public void setUiFontConfig(JsonObject input) throws IOException {
        writeFontConfig(KEY_UI_FONT_CONFIG, input);
    }

    public JsonObject getCodeFontConfig() throws IOException {
        return normalizeFontConfig(readFontConfig(KEY_CODE_FONT_CONFIG));
    }

    public void setCodeFontConfig(JsonObject input) throws IOException {
        writeFontConfig(KEY_CODE_FONT_CONFIG, input);
    }

    public JsonObject getCommitAiConfig() throws IOException {
        JsonObject stored = readFeatureConfig(KEY_COMMIT_AI_CONFIG);
        if (!stored.has("effectiveProvider") || stored.get("effectiveProvider").isJsonNull()) {
            stored.addProperty("effectiveProvider", stored.has("provider") && !stored.get("provider").isJsonNull()
                ? stored.get("provider").getAsString()
                : "codex");
        }
        if (!stored.has("resolutionSource") || stored.get("resolutionSource").isJsonNull()) {
            stored.addProperty("resolutionSource", stored.has("provider") && !stored.get("provider").isJsonNull()
                ? "manual"
                : "auto");
        }
        return stored;
    }

    public void setCommitAiConfig(JsonObject configInput) throws IOException {
        writeFeatureConfig(KEY_COMMIT_AI_CONFIG, configInput, "codex");
    }

    public JsonObject getPromptEnhancerConfig() throws IOException {
        JsonObject stored = readFeatureConfig(KEY_PROMPT_ENHANCER_CONFIG);
        if (!stored.has("effectiveProvider") || stored.get("effectiveProvider").isJsonNull()) {
            stored.addProperty("effectiveProvider", stored.has("provider") && !stored.get("provider").isJsonNull()
                ? stored.get("provider").getAsString()
                : "claude");
        }
        if (!stored.has("resolutionSource") || stored.get("resolutionSource").isJsonNull()) {
            stored.addProperty("resolutionSource", stored.has("provider") && !stored.get("provider").isJsonNull()
                ? "manual"
                : "auto");
        }
        return stored;
    }

    public void setPromptEnhancerConfig(JsonObject configInput) throws IOException {
        writeFeatureConfig(KEY_PROMPT_ENHANCER_CONFIG, configInput, "claude");
    }

    // ==================== Codex Runtime Access ====================

    public boolean isCodexLocalConfigAuthorized() throws IOException {
        JsonObject config = readConfig();
        JsonObject codex = ensureObject(config, KEY_CODEX);
        return codex.has(KEY_CODEX_LOCAL_CONFIG_AUTHORIZED)
            && !codex.get(KEY_CODEX_LOCAL_CONFIG_AUTHORIZED).isJsonNull()
            && codex.get(KEY_CODEX_LOCAL_CONFIG_AUTHORIZED).getAsBoolean();
    }

    public void setCodexLocalConfigAuthorized(boolean authorized) throws IOException {
        JsonObject config = readConfig();
        JsonObject codex = ensureObject(config, KEY_CODEX);
        codex.addProperty(KEY_CODEX_LOCAL_CONFIG_AUTHORIZED, authorized);
        writeConfig(config);
    }

    public String getCodexRuntimeAccessMode() throws IOException {
        JsonObject config = readConfig();
        if (!config.has(KEY_CODEX) || !config.get(KEY_CODEX).isJsonObject()) {
            return CODEX_RUNTIME_ACCESS_INACTIVE;
        }

        JsonObject codex = config.getAsJsonObject(KEY_CODEX);
        String currentId = getNullableString(codex, KEY_CODEX_CURRENT);
        currentId = currentId != null ? currentId.trim() : "";

        if (DEFAULT_CODEX_CLI_LOGIN_PROVIDER_ID.equals(currentId)) {
            return isCodexLocalConfigAuthorized()
                ? CODEX_RUNTIME_ACCESS_CLI_LOGIN
                : CODEX_RUNTIME_ACCESS_INACTIVE;
        }

        if (!currentId.isEmpty()
            && codex.has(KEY_CODEX_PROVIDERS)
            && codex.get(KEY_CODEX_PROVIDERS).isJsonObject()
            && codex.getAsJsonObject(KEY_CODEX_PROVIDERS).has(currentId)) {
            return CODEX_RUNTIME_ACCESS_MANAGED;
        }

        return CODEX_RUNTIME_ACCESS_INACTIVE;
    }

    public JsonObject getCurrentClaudeConfig() throws IOException {
        JsonObject result = new JsonObject();
        result.addProperty("provider", getProvider());
        result.addProperty("permissionMode", getPermissionMode());
        result.addProperty("nodePath", getNodePath());
        String bridgeScriptPath = getBridgeScriptPath();
        result.add("bridgeScriptPath", bridgeScriptPath != null ? gson.toJsonTree(bridgeScriptPath) : JsonNull.INSTANCE);

        String selectedAgentId = getSelectedAgentId();
        if (selectedAgentId != null) {
            result.addProperty("selectedAgentId", selectedAgentId);
            JsonObject agent = getAgent(selectedAgentId);
            if (agent != null) {
                result.add("selectedAgent", agent);
            }
        }
        return result;
    }

    // ==================== Helpers ====================

    private Path getConfigDirectoryPath() {
        return Paths.get(System.getProperty("user.home"), DEFAULT_CONFIG_DIR_NAME);
    }

    private Path getConfigFilePath() {
        return getConfigDirectoryPath().resolve(DEFAULT_CONFIG_FILE_NAME);
    }

    private Path getBackupFilePath() {
        return getConfigDirectoryPath().resolve(DEFAULT_BACKUP_FILE_NAME);
    }

    private void ensureConfigDirectory() throws IOException {
        Files.createDirectories(getConfigDirectoryPath());
    }

    private void backupConfig() {
        try {
            Path configPath = getConfigFilePath();
            if (Files.exists(configPath)) {
                Files.copy(configPath, getBackupFilePath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOG.warn("[CodemossSettings] Failed to backup config: " + e.getMessage());
        }
    }

    private JsonObject createDefaultConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("version", CONFIG_VERSION);

        JsonObject claude = new JsonObject();
        claude.addProperty("current", "");
        claude.add("providers", new JsonObject());
        config.add("claude", claude);

        JsonObject codex = new JsonObject();
        codex.addProperty(KEY_CODEX_CURRENT, "");
        codex.add(KEY_CODEX_PROVIDERS, new JsonObject());
        codex.addProperty(KEY_CODEX_LOCAL_CONFIG_AUTHORIZED, false);
        config.add(KEY_CODEX, codex);

        config.add(KEY_AGENTS, new JsonObject());
        return config;
    }

    private void ensureDefaultSections(JsonObject config) {
        if (!config.has("version")) {
            config.addProperty("version", CONFIG_VERSION);
        }
        ensureObject(config, "claude");
        JsonObject codex = ensureObject(config, KEY_CODEX);
        if (!codex.has(KEY_CODEX_CURRENT)) {
            codex.addProperty(KEY_CODEX_CURRENT, "");
        }
        ensureObject(codex, KEY_CODEX_PROVIDERS);
        if (!codex.has(KEY_CODEX_LOCAL_CONFIG_AUTHORIZED)) {
            codex.addProperty(KEY_CODEX_LOCAL_CONFIG_AUTHORIZED, false);
        }
        ensureObject(config, KEY_AGENTS);
    }

    private JsonObject ensureObject(JsonObject parent, String key) {
        if (!parent.has(key) || !parent.get(key).isJsonObject()) {
            JsonObject object = new JsonObject();
            parent.add(key, object);
            return object;
        }
        return parent.getAsJsonObject(key);
    }

    private JsonObject readSoundConfig() throws IOException {
        JsonObject config = readConfig();
        JsonObject sound = ensureObject(config, KEY_SOUND_NOTIFICATION);
        if (!sound.has(KEY_SOUND_ENABLED)) {
            sound.addProperty(KEY_SOUND_ENABLED, false);
        }
        if (!sound.has(KEY_SOUND_ONLY_WHEN_UNFOCUSED)) {
            sound.addProperty(KEY_SOUND_ONLY_WHEN_UNFOCUSED, false);
        }
        if (!sound.has(KEY_SOUND_SELECTED)) {
            sound.addProperty(KEY_SOUND_SELECTED, "default");
        }
        return sound;
    }

    private JsonObject readFontConfig(String key) throws IOException {
        JsonObject config = readConfig();
        JsonObject font = ensureObject(config, key);
        if (!font.has(KEY_MODE) || font.get(KEY_MODE).isJsonNull()) {
            font.addProperty(KEY_MODE, "followEditor");
        }
        return font.deepCopy();
    }

    private JsonObject normalizeFontConfig(JsonObject stored) {
        JsonObject normalized = new JsonObject();
        String mode = getNullableString(stored, KEY_MODE);
        String effectiveMode = "customFile".equals(mode) ? "customFile" : "followEditor";
        normalized.addProperty("mode", effectiveMode);
        normalized.addProperty("effectiveMode", effectiveMode);
        String customFontPath = getNullableString(stored, KEY_CUSTOM_FONT_PATH);
        if (customFontPath != null) {
            normalized.addProperty(KEY_CUSTOM_FONT_PATH, customFontPath);
        }
        return normalized;
    }

    private void writeFontConfig(String key, JsonObject input) throws IOException {
        JsonObject config = readConfig();
        JsonObject font = ensureObject(config, key);
        String mode = input != null && input.has(KEY_MODE) && !input.get(KEY_MODE).isJsonNull()
            ? input.get(KEY_MODE).getAsString()
            : "followEditor";
        if ("customFile".equals(mode)) {
            font.addProperty(KEY_MODE, "customFile");
            String path = input != null && input.has(KEY_CUSTOM_FONT_PATH) && !input.get(KEY_CUSTOM_FONT_PATH).isJsonNull()
                ? input.get(KEY_CUSTOM_FONT_PATH).getAsString()
                : "";
            font.addProperty(KEY_CUSTOM_FONT_PATH, path);
        } else {
            font.addProperty(KEY_MODE, "followEditor");
            font.remove(KEY_CUSTOM_FONT_PATH);
        }
        writeConfig(config);
    }

    private JsonObject readFeatureConfig(String key) throws IOException {
        JsonObject config = readConfig();
        JsonObject feature = ensureObject(config, key).deepCopy();
        ensureProviderModelDefaults(feature);
        ensureAvailabilityDefaults(feature);
        return feature;
    }

    private void writeFeatureConfig(String key, JsonObject configInput, String defaultProvider) throws IOException {
        JsonObject config = readConfig();
        JsonObject feature = ensureObject(config, key);
        feature.entrySet().clear();
        if (configInput != null && configInput.has("provider")) {
            feature.add("provider", configInput.get("provider"));
        }
        JsonObject models = new JsonObject();
        JsonObject inputModels = configInput != null && configInput.has("models") && configInput.get("models").isJsonObject()
            ? configInput.getAsJsonObject("models")
            : new JsonObject();
        models.addProperty("claude", getModelValue(inputModels, "claude", "claude-sonnet-4-6"));
        models.addProperty("codex", getModelValue(inputModels, "codex", "gpt-5.5"));
        feature.add("models", models);
        JsonObject availability = new JsonObject();
        availability.addProperty("claude", true);
        availability.addProperty("codex", true);
        feature.add("availability", availability);
        String provider = configInput != null && configInput.has("provider") && !configInput.get("provider").isJsonNull()
            ? configInput.get("provider").getAsString()
            : null;
        feature.addProperty("effectiveProvider", provider != null ? provider : defaultProvider);
        feature.addProperty("resolutionSource", provider != null ? "manual" : "auto");
        writeConfig(config);
    }

    private void ensureProviderModelDefaults(JsonObject feature) {
        JsonObject models = feature.has("models") && feature.get("models").isJsonObject()
            ? feature.getAsJsonObject("models")
            : new JsonObject();
        if (!models.has("claude")) {
            models.addProperty("claude", "claude-sonnet-4-6");
        }
        if (!models.has("codex")) {
            models.addProperty("codex", "gpt-5.5");
        }
        feature.add("models", models);
    }

    private void ensureAvailabilityDefaults(JsonObject feature) {
        JsonObject availability = feature.has("availability") && feature.get("availability").isJsonObject()
            ? feature.getAsJsonObject("availability")
            : new JsonObject();
        if (!availability.has("claude")) {
            availability.addProperty("claude", true);
        }
        if (!availability.has("codex")) {
            availability.addProperty("codex", true);
        }
        feature.add("availability", availability);
    }

    private static String getModelValue(JsonObject models, String key, String defaultValue) {
        if (models.has(key) && !models.get(key).isJsonNull()) {
            String value = models.get(key).getAsString();
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return defaultValue;
    }

    private static boolean isValidSandboxMode(@Nullable String sandboxMode) {
        return "workspace-write".equals(sandboxMode) || "danger-full-access".equals(sandboxMode);
    }

    private boolean readBooleanFlag(String key, boolean defaultValue) throws IOException {
        JsonObject config = readConfig();
        return config.has(key) && !config.get(key).isJsonNull()
            ? config.get(key).getAsBoolean()
            : defaultValue;
    }

    private void writeBooleanFlag(String key, boolean value) throws IOException {
        JsonObject config = readConfig();
        config.addProperty(key, value);
        writeConfig(config);
    }

    private boolean readProjectBooleanConfig(String key, String projectPath, boolean defaultValue) throws IOException {
        JsonObject config = readConfig();
        if (!config.has(key) || !config.get(key).isJsonObject()) {
            return defaultValue;
        }
        JsonObject settings = config.getAsJsonObject(key);
        if (projectPath != null && settings.has(projectPath)) {
            return settings.get(projectPath).getAsBoolean();
        }
        if (settings.has("default")) {
            return settings.get("default").getAsBoolean();
        }
        return defaultValue;
    }

    private void writeProjectBooleanConfig(String key, String projectPath, boolean enabled) throws IOException {
        JsonObject config = readConfig();
        JsonObject settings = ensureObject(config, key);
        if (projectPath != null) {
            settings.addProperty(projectPath, enabled);
        }
        settings.addProperty("default", enabled);
        writeConfig(config);
    }

    private static String getNullableString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        String value = object.get(key).getAsString();
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static String extractAgentId(JsonObject agent) {
        if (agent.has("id") && !agent.get("id").isJsonNull()) {
            String value = agent.get("id").getAsString();
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        if (agent.has("name") && !agent.get("name").isJsonNull()) {
            String value = agent.get("name").getAsString();
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return UUID.randomUUID().toString();
    }
}
