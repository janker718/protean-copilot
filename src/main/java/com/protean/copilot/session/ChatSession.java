package com.protean.copilot.session;

import com.google.gson.JsonObject;
import com.protean.copilot.bridge.SdkBridge;
import com.protean.copilot.history.HistoryMetadataService;
import com.protean.copilot.provider.claude.ClaudeSDKBridge;
import com.protean.copilot.settings.SettingsService;
import com.protean.copilot.settings.manager.ProviderManager;
import com.protean.copilot.settings.manager.WorkingDirectoryManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 聊天会话门面。
 *
 * <p>尽量按 cc-gui 的 ClaudeSession 结构组织：
 * 聚合会话元数据、消息历史、发送服务以及生命周期操作。</p>
 */
public class ChatSession {

    private static final Logger LOG = Logger.getInstance(ChatSession.class);

    /**
     * 对话消息。
     */
    public static class Message {
        public enum Type {
            USER, ASSISTANT, SYSTEM, ERROR
        }

        public Type type;
        public String content;
        public long timestamp;
        public JsonObject raw;

        public Message(Type type, String content) {
            this.type = type;
            this.content = content;
            this.timestamp = System.currentTimeMillis();
        }

        public Message(Type type, String content, JsonObject raw) {
            this(type, content);
            this.raw = raw;
        }
    }

    /**
     * 文件附件。
     */
    public static class Attachment {
        public String fileName;
        public String mediaType;
        public String data;

        public Attachment(String fileName, String mediaType, String data) {
            this.fileName = fileName;
            this.mediaType = mediaType;
            this.data = data;
        }
    }

    private final Project project;
    private final SdkBridge sdkBridge;
    private final SessionSendService sendService;

    private final List<Message> messages = new ArrayList<>();

    private volatile String sessionId;
    private volatile String channelId;
    private volatile String runtimeSessionEpoch = UUID.randomUUID().toString();
    private volatile boolean busy;
    private volatile boolean loading;
    private volatile String error;
    private volatile String summary;
    private volatile long lastModifiedTime = System.currentTimeMillis();
    private volatile String cwd;
    private volatile String permissionMode = "bypassPermissions";
    private volatile String model = "default";
    private volatile String provider;
    private volatile String reasoningEffort;
    private volatile List<String> slashCommands = new ArrayList<>();

    private volatile SessionCallbackAdapter callback;

    public ChatSession(Project project, SdkBridge sdkBridge) {
        this.project = project;
        this.sdkBridge = sdkBridge;
        this.sendService = new SessionSendService(project, new SettingsService(), sdkBridge);
        this.provider = ProviderManager.getInstance(project).getActiveProvider();
        this.cwd = WorkingDirectoryManager.getInstance(project).resolveWorkingDirectory();
    }

    public Project getProject() {
        return project;
    }

    public SdkBridge getSdkBridge() {
        return sdkBridge;
    }

    public SessionCallbackAdapter getCallback() {
        return callback;
    }

    public void setCallback(SessionCallbackAdapter callback) {
        this.callback = callback;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getChannelId() {
        return channelId;
    }

    public boolean isBusy() {
        return busy;
    }

    public boolean isLoading() {
        return loading;
    }

    public String getError() {
        return error;
    }

    public List<Message> getMessages() {
        synchronized (messages) {
            return new ArrayList<>(messages);
        }
    }

    public String getSummary() {
        return summary;
    }

    public long getLastModifiedTime() {
        return lastModifiedTime;
    }

    public String getCwd() {
        return cwd;
    }

    public String getPermissionMode() {
        return permissionMode;
    }

    public String getModel() {
        return model;
    }

    public String getProvider() {
        return provider;
    }

    public String getReasoningEffort() {
        return reasoningEffort;
    }

    public String getRuntimeSessionEpoch() {
        return runtimeSessionEpoch;
    }

    public List<String> getSlashCommands() {
        return new ArrayList<>(slashCommands);
    }

    public String ensureSessionId() {
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = UUID.randomUUID().toString();
            LOG.info("Generated sessionId: " + sessionId);
        }
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
    }

    public void setLoading(boolean loading) {
        this.loading = loading;
    }

    public void setError(String error) {
        this.error = error;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public void setCwd(String cwd) {
        this.cwd = WorkingDirectoryManager.getInstance(project).resolveWorkingDirectory(cwd);
        LOG.info("Working directory updated to: " + cwd);
    }

    public void setPermissionMode(String permissionMode) {
        this.permissionMode = permissionMode;
        LOG.info("Permission mode updated to: " + permissionMode);
    }

    public void setModel(String model) {
        this.model = (model == null || model.isBlank()) ? "default" : model.trim();
        LOG.info("Model updated to: " + this.model);
    }

    public void setProvider(String provider) {
        this.provider = ProviderManager.getInstance(project).resolveProvider(provider);
        LOG.info("Provider updated to: " + this.provider);
    }

    public void setReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
        LOG.info("Reasoning effort updated to: " + reasoningEffort);
    }

    public void setRuntimeSessionEpoch(String runtimeSessionEpoch) {
        this.runtimeSessionEpoch = (runtimeSessionEpoch == null || runtimeSessionEpoch.isBlank())
            ? UUID.randomUUID().toString()
            : runtimeSessionEpoch;
    }

    public String rotateRuntimeSessionEpoch() {
        String epoch = UUID.randomUUID().toString();
        this.runtimeSessionEpoch = epoch;
        return epoch;
    }

    public void setSlashCommands(List<String> slashCommands) {
        this.slashCommands = slashCommands != null ? new ArrayList<>(slashCommands) : new ArrayList<>();
    }

    public void addMessage(Message message) {
        synchronized (messages) {
            messages.add(message);
        }
        updateLastModifiedTime();
    }

    public void clearMessages() {
        synchronized (messages) {
            messages.clear();
        }
    }

    public void updateLastModifiedTime() {
        this.lastModifiedTime = System.currentTimeMillis();
    }

    /**
     * 设置会话信息（会话 ID 和工作目录）。
     */
    public void setSessionInfo(String newSessionId, String newCwd) {
        if (newSessionId != null) {
            sessionId = newSessionId;
            SessionCallbackAdapter sessionCallback = callback;
            if (sessionCallback != null) {
                sessionCallback.onSessionIdReceived(newSessionId);
            }
        }
        if (newCwd != null) {
            setCwd(newCwd);
        }
    }

    /**
     * 对齐 ClaudeSession 的会话启动入口。
     * 当前桥接没有独立 launchChannel API，因此这里只建立本地会话元数据。
     */
    public CompletableFuture<String> launchClaude() {
        ClaudeSDKBridge claudeBridge = sdkBridge.getClaudeBridge();
        if (claudeBridge == null || !claudeBridge.isRunning()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("Claude SDK bridge is not running")
            );
        }
        if (channelId != null && !channelId.isBlank()) {
            return CompletableFuture.completedFuture(channelId);
        }

        channelId = UUID.randomUUID().toString();
        error = null;
        ensureSessionId();
        return CompletableFuture.completedFuture(channelId);
    }

    @Deprecated
    public CompletableFuture<Void> send(String input) {
        return send(input, null, null, null, null, null, null);
    }

    public CompletableFuture<Void> send(String input, String requestedPermissionMode, String requestedReasoningEffort) {
        return send(input, null, null, null, requestedPermissionMode, requestedReasoningEffort, null);
    }

    public CompletableFuture<Void> send(String input, String agentPrompt) {
        return send(input, null, agentPrompt, null, null, null, null);
    }

    public CompletableFuture<Void> send(String input, String agentPrompt, List<String> fileTagPaths) {
        return send(input, null, agentPrompt, fileTagPaths, null, null, null);
    }

    public CompletableFuture<Void> send(
        String input,
        String agentPrompt,
        List<String> fileTagPaths,
        String requestedPermissionMode
    ) {
        return send(input, null, agentPrompt, fileTagPaths, requestedPermissionMode, null, null);
    }

    public CompletableFuture<Void> send(String input, List<Attachment> attachments, String agentPrompt) {
        return send(input, attachments, agentPrompt, null, null, null, null);
    }

    public CompletableFuture<Void> send(
        String input,
        List<Attachment> attachments,
        String agentPrompt,
        List<String> fileTagPaths,
        String requestedPermissionMode,
        String requestedReasoningEffort,
        String requestedCodexFastMode
    ) {
        String normalizedInput = input != null ? input.trim() : "";
        Message userMessage = buildUserMessage(normalizedInput, attachments);
        addMessage(userMessage);
        updateSummaryFromInput(normalizedInput);
        HistoryMetadataService.getInstance(project).updateFromSession(this);

        error = null;
        busy = true;
        loading = true;

        if (attachments != null && !attachments.isEmpty()) {
            LOG.warn("Attachments payload received, but backend bridge currently sends text-only queries");
            SessionCallbackAdapter adapter = callback;
            if (adapter != null) {
                adapter.updateStatus("Attachments are not yet forwarded to Claude SDK; sending text only.");
            }
        }

        return launchClaude().thenCompose(chId ->
            sendService.sendMessageToProvider(
                chId,
                this,
                normalizedInput,
                requestedPermissionMode,
                requestedReasoningEffort
            )
        ).whenComplete((v, ex) -> {
            busy = false;
            loading = false;
            error = ex != null ? ex.getMessage() : null;
            updateLastModifiedTime();
            HistoryMetadataService.getInstance(project).updateFromSession(this);
        });
    }

    private Message buildUserMessage(String normalizedInput, List<Attachment> attachments) {
        String content = normalizedInput;
        if (attachments != null && !attachments.isEmpty()) {
            List<String> labels = new ArrayList<>();
            for (Attachment attachment : attachments) {
                if (attachment != null && attachment.fileName != null && !attachment.fileName.isBlank()) {
                    labels.add(attachment.fileName);
                }
            }
            if (!labels.isEmpty()) {
                String suffix = "\n\n[attachments] " + String.join(", ", labels);
                content = normalizedInput.isEmpty() ? suffix.trim() : normalizedInput + suffix;
            }
        }
        return new Message(Message.Type.USER, content);
    }

    private void updateSummaryFromInput(String normalizedInput) {
        if (summary != null || normalizedInput == null || normalizedInput.isEmpty()) {
            return;
        }
        summary = normalizedInput.length() > 45
            ? normalizedInput.substring(0, 45) + "..."
            : normalizedInput;
    }

    /**
     * 中断当前会话。
     */
    public CompletableFuture<Void> interrupt() {
        if (channelId == null || channelId.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        ClaudeSDKBridge claudeBridge = sdkBridge.getClaudeBridge();
        if (claudeBridge != null && claudeBridge.isRunning() && sessionId != null) {
            return claudeBridge.interrupt(sessionId).whenComplete((v, ex) -> {
                busy = false;
                loading = false;
                if (ex == null) {
                    error = null;
                }
                updateLastModifiedTime();
                HistoryMetadataService.getInstance(project).updateFromSession(this);
            });
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 重启当前会话。
     */
    public CompletableFuture<Void> restart() {
        return interrupt().thenCompose(v -> {
            channelId = null;
            sessionId = null;
            error = null;
            busy = false;
            loading = false;
            return launchClaude().thenApply(chId -> null);
        });
    }

    /**
     * 从服务器加载会话数据（历史记录）。
     * 当前仓库尚未有完整历史恢复能力，保留门面方法与 ClaudeSession 对齐。
     */
    public CompletableFuture<Void> loadFromServer() {
        return CompletableFuture.completedFuture(null);
    }
}
