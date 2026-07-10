package com.protean.copilot.provider.codex;

import com.google.gson.JsonObject;
import com.protean.copilot.provider.common.BaseSDKBridge;
import com.protean.copilot.settings.CodemossSettingsService;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class CodexSDKBridge extends BaseSDKBridge {

    private final CodemossSettingsService settingsService = new CodemossSettingsService();

    static final String SANDBOX_READ_ONLY = "read-only";
    static final String SANDBOX_WORKSPACE_WRITE = "workspace-write";
    static final String SANDBOX_DANGER_FULL_ACCESS = "danger-full-access";

    static final String APPROVAL_NEVER = "never";
    static final String APPROVAL_ON_REQUEST = "on-request";
    static final String APPROVAL_UNTRUSTED = "untrusted";

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
        String runtime = readOptionalString(message, "runtime");
        String hint = readOptionalString(message, "hint");
        if (sdkAvailable) {
            log().info("Codex bridge ready"
                + (runtime != null ? " (" + runtime + ")" : ""));
        } else {
            log().warn("Codex bridge ready, but @openai/codex-sdk is unavailable"
                + (hint != null ? ": " + hint : ""));
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

    @Override
    protected JsonObject buildQueryMessage(
        String sessionId,
        String prompt,
        String cwd,
        String model,
        String permissionMode,
        String reasoningEffort
    ) {
        JsonObject message = super.buildQueryMessage(sessionId, prompt, cwd, model, permissionMode, reasoningEffort);
        enrichProviderOptions(message, cwd, permissionMode, true);
        return message;
    }

    @Override
    protected JsonObject buildResumeMessage(String sessionId, String prompt, String cwd) {
        JsonObject message = super.buildResumeMessage(sessionId, prompt, cwd);
        enrichProviderOptions(message, cwd, "default", false);
        return message;
    }

    @Override
    protected JsonObject buildPrewarmMessage(String cwd, String model, String permissionMode) {
        JsonObject message = super.buildPrewarmMessage(cwd, model, permissionMode);
        enrichProviderOptions(message, cwd, permissionMode, true);
        return message;
    }

    private void enrichProviderOptions(JsonObject message, String cwd, String permissionMode, boolean allowReasoningOverrides) {
        String normalizedPermission = normalizePermissionMode(permissionMode);
        String configuredSandbox = readConfiguredSandboxMode(cwd);
        message.addProperty("permissionMode", normalizedPermission);
        message.addProperty("sandboxMode", resolveSandboxMode(normalizedPermission, configuredSandbox));
        message.addProperty("approvalPolicy", resolveApprovalPolicy(normalizedPermission));
        message.addProperty("skipGitRepoCheck", true);
        message.addProperty("runtimeAccessMode", readRuntimeAccessMode());
        if (cwd != null && !cwd.isBlank()) {
            message.addProperty("workingDirectory", cwd);
        }
        if (!allowReasoningOverrides) {
            message.remove("reasoningEffort");
        }
    }

    static String normalizePermissionMode(@Nullable String permissionMode) {
        return permissionMode == null || permissionMode.isBlank() ? "default" : permissionMode.trim();
    }

    static String resolveSandboxMode(String permissionMode, @Nullable String configuredSandboxMode) {
        String normalizedPermission = normalizePermissionMode(permissionMode);
        if ("plan".equals(normalizedPermission) || "sandbox".equals(normalizedPermission)) {
            return SANDBOX_READ_ONLY;
        }
        if ("acceptEdits".equals(normalizedPermission)) {
            return SANDBOX_WORKSPACE_WRITE;
        }
        if ("bypassPermissions".equals(normalizedPermission) || "yolo".equals(normalizedPermission)) {
            return sanitizeSandboxMode(configuredSandboxMode, SANDBOX_DANGER_FULL_ACCESS);
        }
        return sanitizeSandboxMode(configuredSandboxMode, SANDBOX_WORKSPACE_WRITE);
    }

    static String resolveApprovalPolicy(String permissionMode) {
        String normalizedPermission = normalizePermissionMode(permissionMode);
        if ("bypassPermissions".equals(normalizedPermission) || "yolo".equals(normalizedPermission)) {
            return APPROVAL_NEVER;
        }
        if ("acceptEdits".equals(normalizedPermission)) {
            return APPROVAL_ON_REQUEST;
        }
        return APPROVAL_UNTRUSTED;
    }

    private static String sanitizeSandboxMode(@Nullable String configuredSandboxMode, String fallback) {
        if (SANDBOX_READ_ONLY.equals(configuredSandboxMode)
            || SANDBOX_WORKSPACE_WRITE.equals(configuredSandboxMode)
            || SANDBOX_DANGER_FULL_ACCESS.equals(configuredSandboxMode)) {
            return configuredSandboxMode;
        }
        return fallback;
    }

    private String readConfiguredSandboxMode(@Nullable String cwd) {
        try {
            return settingsService.getCodexSandboxMode(cwd);
        } catch (IOException e) {
            log().warn("读取 Codex sandbox 配置失败: " + e.getMessage());
            return null;
        }
    }

    private String readRuntimeAccessMode() {
        try {
            return settingsService.getCodexRuntimeAccessMode();
        } catch (IOException e) {
            log().warn("读取 Codex runtime access 配置失败: " + e.getMessage());
            return CodemossSettingsService.CODEX_RUNTIME_ACCESS_INACTIVE;
        }
    }
}
