package com.protean.copilot.session;

import com.protean.copilot.handler.PermissionHandler;
import com.intellij.openapi.diagnostic.Logger;
import java.util.function.BooleanSupplier;

/**
 * 将会话回调从 SDK 桥接桥接到聊天窗口前端。
 * 提供流式响应事件、消息更新和错误处理的前端通知方法。
 *
 * <p>该适配器从参考实现移植而来，已扩展支持完整的流式事件回调。
 * 所有方法在执行前检查 {@code active} 状态，确保在适配器停用后不再发送消息。
 */
public class SessionCallbackAdapter {

    private static final Logger LOG = Logger.getInstance(SessionCallbackAdapter.class);

    /**
     * 用于从回调适配器调用 JavaScript 的函数式接口。
     * 实现类负责在 EDT 上安全地执行 JS 调用。
     */
    @FunctionalInterface
    public interface JsTarget {
        /**
         * 在 webview 中调用 JavaScript 函数。
         *
         * @param functionName JavaScript 函数名
         * @param args         传递给函数的参数
         */
        void callJavaScript(String functionName, String... args);
    }

    /** 流消息合并器 —— 批量流式更新以减少 webview 推送频率 */
    private final StreamMessageCoalescer streamCoalescer;

    /** JS 调用目标 —— 封装对 JCEF webview 的 JavaScript 调用 */
    private final JsTarget jsTarget;

    /** 权限处理器 —— 管理待处理的权限请求 */
    private final PermissionHandler permissionHandler;

    /** 斜杠命令是否已获取的检查器 */
    private final BooleanSupplier slashCommandsFetched;

    /** 流结束回调 —— 在流式响应结束时执行 */
    private final Runnable onStreamEnded;

    /** 适配器是否处于活动状态 */
    private volatile boolean active = true;

    /**
     * 创建会话回调适配器。
     *
     * @param streamCoalescer     流消息合并器
     * @param jsTarget            JS 调用目标
     * @param permissionHandler   权限处理器
     * @param slashCommandsFetched 斜杠命令获取状态检查器
     * @param onStreamEnded       流结束回调
     */
    public SessionCallbackAdapter(
        StreamMessageCoalescer streamCoalescer,
        JsTarget jsTarget,
        PermissionHandler permissionHandler,
        BooleanSupplier slashCommandsFetched,
        Runnable onStreamEnded
    ) {
        this.streamCoalescer = streamCoalescer;
        this.jsTarget = jsTarget;
        this.permissionHandler = permissionHandler;
        this.slashCommandsFetched = slashCommandsFetched;
        this.onStreamEnded = onStreamEnded;
    }

    /**
     * 停用适配器，阻止后续回调。
     * 停用后所有回调方法变为空操作。
     */
    public void deactivate() {
        active = false;
    }

    /**
     * 释放适配器并清理资源。
     */
    public void dispose() {
        deactivate();
        LOG.info("SessionCallbackAdapter disposed");
    }

    /**
     * 检查适配器是否处于活动状态。
     *
     * @return 如果适配器仍可接收回调，返回 true
     */
    public boolean isActive() {
        return active;
    }

    // ==================== 原有回调 ====================

    /**
     * 当从服务器收到会话 ID 时调用。
     * 将 sessionId 同步到前端用于历史记录和恢复。
     *
     * @param sessionId 服务器分配的会话唯一标识
     */
    public void onSessionIdReceived(String sessionId) {
        if (!active) return;
        try {
            jsTarget.callJavaScript("updateSessionId", sessionId);
        } catch (Exception e) {
            LOG.warn("Failed to send session ID to frontend: " + e.getMessage());
        }
    }

    // ==================== 流式事件回调 ====================

    /**
     * 当流式响应开始时调用。
     * 通知前端进入加载状态，开始显示流式输出。
     */
    public void onStreamStart() {
        if (!active) return;
        try {
            streamCoalescer.onStreamStart();
            jsTarget.callJavaScript("onStreamStart");
            jsTarget.callJavaScript("showLoading", "true");
        } catch (Exception e) {
            LOG.warn("流开始通知失败: " + e.getMessage());
        }
    }

    /**
     * 当收到内容增量时调用。
     * 将 AI 生成的新文本块推送给前端追加显示。
     *
     * @param delta 新增的文本内容
     */
    public void onContentDelta(String delta) {
        if (!active) return;
        try {
            streamCoalescer.onStreamStart();
            jsTarget.callJavaScript("onContentDelta", delta);
        } catch (Exception e) {
            LOG.warn("内容增量发送失败: " + e.getMessage());
        }
    }

    /**
     * 当收到思维增量时调用。
     * 将 Claude 的思考/推理过程推送给前端展示。
     *
     * @param delta 新增的思考文本内容
     */
    public void onThinkingDelta(String delta) {
        if (!active) return;
        try {
            jsTarget.callJavaScript("onThinkingDelta", delta);
        } catch (Exception e) {
            LOG.warn("思维增量发送失败: " + e.getMessage());
        }
    }

    /**
     * 当流式响应结束时调用。
     * 通知前端停止加载动画，完成消息渲染。
     */
    public void onStreamEnd() {
        if (!active) return;
        try {
            jsTarget.callJavaScript("onStreamEnd");
            jsTarget.callJavaScript("showLoading", "false");
            streamCoalescer.onStreamEnd();
            onStreamEnded.run();
        } catch (Exception e) {
            LOG.warn("流结束通知失败: " + e.getMessage());
        }
    }

    /**
     * 当收到完整的消息更新快照时调用。
     * 将整个对话消息列表推送给前端替换当前状态。
     *
     * @param messagesJson 消息列表的 JSON 字符串
     */
    public void updateMessages(String messagesJson) {
        if (!active) return;
        try {
            jsTarget.callJavaScript("updateMessages", messagesJson);
        } catch (Exception e) {
            LOG.warn("消息更新发送失败: " + e.getMessage());
        }
    }

    /**
     * 显示错误消息。
     * 将错误信息推送到前端以 Toast 或内联形式展示。
     *
     * @param message 错误描述文本
     */
    public void showError(String message) {
        if (!active) return;
        try {
            jsTarget.callJavaScript("addErrorMessage", message);
            jsTarget.callJavaScript("showLoading", "false");
        } catch (Exception e) {
            LOG.warn("错误消息发送失败: " + e.getMessage());
        }
    }

    /**
     * 当内容块重置时调用。
     * 在工具调用完成、新的 assistant 回复开始前通知前端重置渲染状态。
     */
    public void onBlockReset() {
        if (!active) return;
        try {
            jsTarget.callJavaScript("onBlockReset");
        } catch (Exception e) {
            LOG.warn("块重置通知失败: " + e.getMessage());
        }
    }

    /**
     * 当收到工具调用事件时调用。
     * 通知前端显示正在使用的工具信息。
     *
     * @param toolName  工具名称
     * @param toolInput 工具输入参数的 JSON 字符串
     */
    public void onToolUse(String toolName, String toolInput) {
        if (!active) return;
        try {
            jsTarget.callJavaScript("onToolUse", toolName, toolInput);
        } catch (Exception e) {
            LOG.warn("工具调用通知失败: " + e.getMessage());
        }
    }

    /**
     * 当收到工具调用结果时调用。
     *
     * @param toolUseId 工具调用 ID
     * @param isError   结果是否为错误
     */
    public void onToolResult(String toolUseId, String isError) {
        if (!active) return;
        try {
            jsTarget.callJavaScript("onToolResult", toolUseId, isError);
        } catch (Exception e) {
            LOG.warn("工具结果通知失败: " + e.getMessage());
        }
    }

    /**
     * 发送流式心跳以防止看门狗误触发。
     * 在工具执行等长时间没有内容增量的阶段发送，
     * 告知 {@code WebviewWatchdog} 桥接仍然健康。
     */
    public void onStreamingHeartbeat() {
        if (!active) return;
        try {
            jsTarget.callJavaScript("onStreamingHeartbeat");
        } catch (Exception e) {
            LOG.warn("流式心跳发送失败: " + e.getMessage());
        }
    }

    /**
     * 通知前端桥接已就绪。
     *
     * @param version      SDK 版本号
     * @param sdkAvailable SDK 是否已安装并可用
     */
    public void onBridgeReady(String version, String sdkAvailable) {
        if (!active) return;
        try {
            jsTarget.callJavaScript("onBridgeReady", version, sdkAvailable);
        } catch (Exception e) {
            LOG.warn("桥接就绪通知失败: " + e.getMessage());
        }
    }

    /**
     * 更新前端状态文本。
     *
     * @param status 状态描述文本
     */
    public void updateStatus(String status) {
        if (!active) return;
        try {
            jsTarget.callJavaScript("updateStatus", status);
        } catch (Exception e) {
            LOG.warn("状态更新失败: " + e.getMessage());
        }
    }
}
