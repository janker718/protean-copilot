package com.protean.copilot.session;

import com.protean.copilot.bridge.SdkBridge;
import com.protean.copilot.provider.claude.ClaudeSDKBridge;
import com.protean.copilot.settings.SettingsService;
import com.protean.copilot.settings.manager.WorkingDirectoryManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * 负责消息发送编排，使 ChatSession 保持为轻量级会话门面。
 *
 * <p>代码结构参考 cc-gui 的 SessionSendService，
 * 当前仅落实现有仓库已接通的 Claude/Protean provider 链路。</p>
 */
public class SessionSendService {

    private static final Logger LOG = Logger.getInstance(SessionSendService.class);

    private static final Set<String> VALID_PERMISSION_MODES = Set.of(
        "default",
        "plan",
        "acceptEdits",
        "bypassPermissions"
    );

    private static final Set<String> VALID_REASONING_EFFORTS = Set.of(
        "low",
        "medium",
        "high"
    );

    private final Project project;
    private final SettingsService settingsService;
    private final SdkBridge sdkBridge;

    public SessionSendService(Project project, SdkBridge sdkBridge) {
        this.project = project;
        this.settingsService = new SettingsService();
        this.sdkBridge = sdkBridge;
    }

    public CompletableFuture<Void> sendMessageToProvider(
        String channelId,
        ChatSession session,
        String input,
        String requestedPermissionMode,
        String requestedReasoningEffort
    ) {
        if (session == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Session is not initialized"));
        }

        String currentProvider = normalizeProvider(session.getProvider());
        String normalizedRequestedMode = normalizeRequestedPermissionMode(requestedPermissionMode);
        String effectivePermissionMode = resolveEffectivePermissionMode(
            currentProvider,
            normalizedRequestedMode,
            session.getPermissionMode()
        );
        String normalizedRequestedEffort = normalizeRequestedReasoningEffort(requestedReasoningEffort);

        updateSessionStateForSend(session, effectivePermissionMode, normalizedRequestedEffort);

        LOG.info(
            "[SessionSend] provider=" + currentProvider
                + ", channelId=" + (channelId != null ? channelId : "(none)")
                + ", sessionId=" + (session.getSessionId() != null ? session.getSessionId() : "(new)")
                + ", cwd=" + session.getCwd()
                + ", model=" + session.getModel()
                + ", permissionMode=" + effectivePermissionMode
                + ", reasoningEffort="
                + (normalizedRequestedEffort != null ? normalizedRequestedEffort : session.getReasoningEffort())
        );

        if ("claude".equals(currentProvider)) {
            return sendToClaude(session, input, effectivePermissionMode, normalizedRequestedEffort);
        }

        return CompletableFuture.failedFuture(
            new IllegalArgumentException("Unsupported provider: " + currentProvider)
        );
    }

    public void updateSessionStateForSend(
        ChatSession session,
        String effectivePermissionMode,
        String normalizedRequestedEffort
    ) {
        session.ensureSessionId();

        if (effectivePermissionMode != null) {
            session.setPermissionMode(effectivePermissionMode);
            settingsService.setPermissionMode(effectivePermissionMode);
        }
        if (normalizedRequestedEffort != null) {
            session.setReasoningEffort(normalizedRequestedEffort);
        }
        if (session.getCwd() == null || session.getCwd().isBlank()) {
            session.setCwd(WorkingDirectoryManager.getInstance(project).resolveWorkingDirectory());
        }

        SessionCallbackAdapter callback = session.getCallback();
        if (callback != null) {
            callback.onSessionIdReceived(session.getSessionId());
            callback.onStreamStart();
        }
    }

    public static String normalizeRequestedReasoningEffort(String effort) {
        if (effort == null) {
            return null;
        }
        String trimmed = effort.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (VALID_REASONING_EFFORTS.contains(trimmed)) {
            return trimmed;
        }
        LOG.warn("[ReasoningEffort] Invalid requested reasoningEffort ignored: " + effort);
        return null;
    }

    public static String normalizeRequestedPermissionMode(String mode) {
        if (mode == null) {
            return null;
        }
        String trimmed = mode.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (VALID_PERMISSION_MODES.contains(trimmed)) {
            return trimmed;
        }
        LOG.warn("[PermissionMode] Invalid requested permissionMode ignored: " + mode);
        return null;
    }

    public static String resolveEffectivePermissionMode(String provider, String requestedMode, String sessionMode) {
        String resolvedMode = requestedMode;
        if (resolvedMode == null) {
            resolvedMode = normalizeRequestedPermissionMode(sessionMode);
        }
        if (resolvedMode == null) {
            resolvedMode = "default";
        }

        if ("codex".equals(provider) && "plan".equals(resolvedMode)) {
            return "default";
        }
        return resolvedMode;
    }

    private CompletableFuture<Void> sendToClaude(
        ChatSession session,
        String input,
        String effectivePermissionMode,
        String requestedReasoningEffort
    ) {
        ClaudeSDKBridge bridge = sdkBridge.getClaudeBridge();
        if (bridge == null || !bridge.isRunning()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Claude SDK bridge is not running")
            );
        }

        return bridge.query(
            session.getSessionId(),
            input != null ? input : "",
            session.getCwd(),
            session.getModel(),
            effectivePermissionMode,
            requestedReasoningEffort != null ? requestedReasoningEffort : session.getReasoningEffort()
        );
    }

    private static String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return "claude";
        }
        return provider.trim();
    }
}
