package com.protean.copilot.handler.provider;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;
import com.protean.copilot.handler.core.BaseMessageHandler;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.settings.CodemossSettingsService;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles persisted Claude and Codex provider configuration actions from the
 * Settings webview. Keeping these actions out of SettingsHandler prevents the
 * generic settings surface from owning provider-specific lifecycle rules.
 */
public final class ProviderHandler extends BaseMessageHandler {

    private static final Logger LOG = Logger.getInstance(ProviderHandler.class);

    private static final List<String> SUPPORTED_TYPES = List.of(
        "get_providers",
        "add_provider",
        "update_provider",
        "delete_provider",
        "switch_provider",
        "sort_providers",
        "get_active_provider",
        "get_thinking_enabled",
        "set_thinking_enabled",
        "get_current_claude_config",
        "get_codex_providers",
        "add_codex_provider",
        "update_codex_provider",
        "delete_codex_provider",
        "switch_codex_provider",
        "revoke_codex_local_config_authorization",
        "sort_codex_providers",
        "get_active_codex_provider",
        "get_current_codex_config"
    );

    private final Gson gson = new Gson();
    private final CodemossSettingsService settingsService;

    public ProviderHandler(HandlerContext context) {
        this(context, new CodemossSettingsService());
    }

    ProviderHandler(HandlerContext context, CodemossSettingsService settingsService) {
        super(context);
        this.settingsService = settingsService;
    }

    @Override
    public boolean handle(String type, String content) {
        if (!SUPPORTED_TYPES.contains(type)) {
            return false;
        }

        try {
            switch (type) {
                case "get_providers" -> pushClaudeProviders();
                case "add_provider" -> {
                    settingsService.addClaudeProvider(parseObject(content));
                    pushClaudeProviders();
                }
                case "update_provider" -> {
                    JsonObject data = parseObject(content);
                    settingsService.updateClaudeProvider(requiredString(data, "id"), object(data, "updates"));
                    pushClaudeProviders();
                }
                case "delete_provider" -> {
                    settingsService.deleteClaudeProvider(requiredString(parseObject(content), "id"));
                    pushClaudeProviders();
                }
                case "switch_provider" -> {
                    settingsService.switchClaudeProvider(requiredString(parseObject(content), "id"));
                    pushClaudeProviders();
                    pushActiveClaudeProvider();
                }
                case "sort_providers" -> {
                    settingsService.saveClaudeProviderOrder(stringArray(parseObject(content), "orderedIds"));
                    pushClaudeProviders();
                }
                case "get_active_provider" -> pushActiveClaudeProvider();
                case "get_thinking_enabled" -> callJavaScript("updateThinkingEnabled", gson.toJson(settingsService.isThinkingEnabled()));
                case "set_thinking_enabled" -> settingsService.setThinkingEnabled(parseBoolean(content));
                case "get_current_claude_config" -> callJavaScript("updateCurrentClaudeConfig", gson.toJson(settingsService.getCurrentClaudeConfig()));
                case "get_codex_providers" -> pushCodexProviders();
                case "add_codex_provider" -> {
                    settingsService.addCodexProvider(parseObject(content));
                    pushCodexProviders();
                }
                case "update_codex_provider" -> {
                    JsonObject data = parseObject(content);
                    settingsService.updateCodexProvider(requiredString(data, "id"), object(data, "updates"));
                    pushCodexProviders();
                }
                case "delete_codex_provider" -> {
                    settingsService.deleteCodexProvider(requiredString(parseObject(content), "id"));
                    pushCodexProviders();
                }
                case "switch_codex_provider" -> {
                    settingsService.switchCodexProvider(requiredString(parseObject(content), "id"));
                    pushCodexProviders();
                    pushActiveCodexProvider();
                }
                case "revoke_codex_local_config_authorization" -> revokeCodexLocalConfigAuthorization(content);
                case "sort_codex_providers" -> {
                    settingsService.saveCodexProviderOrder(stringArray(parseObject(content), "orderedIds"));
                    pushCodexProviders();
                }
                case "get_active_codex_provider" -> pushActiveCodexProvider();
                case "get_current_codex_config" -> callJavaScript("updateCurrentCodexConfig", gson.toJson(settingsService.getCurrentCodexConfig()));
                default -> {
                    return false;
                }
            }
            return true;
        } catch (Exception exception) {
            LOG.warn("Provider settings action failed: " + type, exception);
            callJavaScript("showError", "Unable to save provider settings: " + exception.getMessage());
            return true;
        }
    }

    @Override
    public List<String> getSupportedTypes() {
        return SUPPORTED_TYPES;
    }

    private void revokeCodexLocalConfigAuthorization(String content) throws Exception {
        JsonObject data = parseObject(content);
        settingsService.setCodexLocalConfigAuthorized(false);
        String fallbackId = optionalString(data, "fallbackProviderId");
        settingsService.switchCodexProvider(fallbackId == null || fallbackId.isBlank() ? "__disabled__" : fallbackId);
        pushCodexProviders();
        pushActiveCodexProvider();
    }

    private void pushClaudeProviders() throws Exception {
        callJavaScript("updateProviders", gson.toJson(settingsService.getClaudeProviders()));
    }

    private void pushCodexProviders() throws Exception {
        callJavaScript("updateCodexProviders", gson.toJson(settingsService.getCodexProviders()));
    }

    private void pushActiveClaudeProvider() throws Exception {
        callJavaScript("updateActiveProvider", gson.toJson(settingsService.getActiveClaudeProvider()));
    }

    private void pushActiveCodexProvider() throws Exception {
        callJavaScript("updateActiveCodexProvider", gson.toJson(settingsService.getActiveCodexProvider()));
    }

    private static JsonObject parseObject(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Provider payload is required");
        }
        var element = JsonParser.parseString(content);
        if (!element.isJsonObject()) {
            throw new IllegalArgumentException("Provider payload must be a JSON object");
        }
        return element.getAsJsonObject();
    }

    private static JsonObject object(JsonObject value, String key) {
        if (!value.has(key) || !value.get(key).isJsonObject()) {
            throw new IllegalArgumentException("Missing object field: " + key);
        }
        return value.getAsJsonObject(key);
    }

    private static String requiredString(JsonObject value, String key) {
        String result = optionalString(value, key);
        if (result == null || result.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        return result;
    }

    private static String optionalString(JsonObject value, String key) {
        return value.has(key) && !value.get(key).isJsonNull() ? value.get(key).getAsString() : null;
    }

    private static List<String> stringArray(JsonObject value, String key) {
        if (!value.has(key) || !value.get(key).isJsonArray()) {
            throw new IllegalArgumentException("Missing array field: " + key);
        }
        JsonArray values = value.getAsJsonArray(key);
        List<String> result = new ArrayList<>();
        values.forEach(item -> {
            if (item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                result.add(item.getAsString());
            }
        });
        return result;
    }

    private static boolean parseBoolean(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Thinking setting payload is required");
        }
        var value = JsonParser.parseString(content);
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            return value.getAsBoolean();
        }
        if (value.isJsonObject() && value.getAsJsonObject().has("enabled")) {
            return value.getAsJsonObject().get("enabled").getAsBoolean();
        }
        throw new IllegalArgumentException("Thinking setting payload must contain a boolean enabled value");
    }
}
