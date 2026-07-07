package com.protean.copilot.session;

import com.protean.copilot.bridge.SdkBridge;
import com.protean.copilot.provider.claude.ClaudeSDKBridge;
import com.intellij.openapi.project.Project;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * 持有聊天会话状态。替代参考实现中的 ClaudeSession。
 *
 * <p>会话对象跟踪当前对话的元数据（模型、工作目录、权限模式等），
 * 并提供中断和恢复等生命周期操作。实际的 AI 通信通过
 * {@link SdkBridge} → {@link ClaudeSDKBridge} → Node.js 进程 链路完成。
 */
public class ChatSession {
    /** IntelliJ 项目 */
    public final Project project;

    /** 统一的 SDK 桥接 */
    public final SdkBridge sdkBridge;

    /** AI 提供商名称 */
    public String provider = "protean";

    /** 模型标识符 */
    public String model = "default";

    /** 会话唯一标识（由 SDK 服务器分配或本地生成） */
    public String sessionId = null;

    /** 当前工作目录 */
    public String cwd;

    /** 权限模式（bypassPermissions / default / plan / acceptEdits） */
    public String permissionMode = "bypassPermissions";

    /** 推理深度（low / medium / high，可为 null 表示默认） */
    public String reasoningEffort = null;

    /** 错误状态（最近一次错误的消息） */
    public String error = null;

    /** 会话回调适配器 */
    private volatile SessionCallbackAdapter callback = null;

    /**
     * 创建新的聊天会话。
     *
     * @param project   IntelliJ 项目
     * @param sdkBridge SDK 桥接实例
     */
    public ChatSession(Project project, SdkBridge sdkBridge) {
        this.project = project;
        this.sdkBridge = sdkBridge;
        String basePath = project.getBasePath();
        if (basePath != null) {
            this.cwd = basePath;
        } else {
            String userHome = System.getProperty("user.home");
            this.cwd = Objects.requireNonNullElse(userHome, ".");
        }
    }

    /**
     * 获取会话回调适配器。
     */
    public SessionCallbackAdapter getCallback() {
        return callback;
    }

    /**
     * 设置会话回调适配器。
     */
    public void setCallback(SessionCallbackAdapter callback) {
        this.callback = callback;
    }

    /**
     * 设置会话信息（会话 ID 和工作目录）。
     *
     * @param newSessionId 新的会话 ID（null 表示保持不变）
     * @param newCwd       新的工作目录（null 表示保持不变）
     */
    public void setSessionInfo(String newSessionId, String newCwd) {
        if (newSessionId != null) sessionId = newSessionId;
        if (newCwd != null) cwd = newCwd;
    }

    /**
     * 中断当前会话。
     * 如果 Claude SDK 桥接可用且 sessionId 存在，则通过桥接发送中断命令；
     * 否则直接返回已完成的 Future（桩行为）。
     *
     * @return 一个在中断完成时完成的 future
     */
    public CompletableFuture<Void> interrupt() {
        ClaudeSDKBridge claudeBridge = sdkBridge.getClaudeBridge();
        if (claudeBridge != null && claudeBridge.isRunning() && sessionId != null) {
            return claudeBridge.interrupt(sessionId);
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 从服务器加载会话数据（历史记录）。
     *
     * @return 一个在加载完成时完成的 future
     */
    public CompletableFuture<Void> loadFromServer() {
        // 桩：历史上的会话恢复尚未完全实现
        // 后续将委托给 ClaudeSDKBridge 加载历史消息
        return CompletableFuture.completedFuture(null);
    }
}
