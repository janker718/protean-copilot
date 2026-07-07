package com.protean.copilot.provider.claude;

import com.google.gson.JsonObject;
import com.protean.copilot.provider.common.BaseSDKBridge;

/**
 * Claude SDK 桥接 —— 对接 npm {@code @anthropic-ai/claude-code} 的具体实现。
 *
 * <p>继承自 {@link BaseSDKBridge}，复用所有通用的进程管理、JSON-line 协议、
 * 会话生命周期和流式事件处理逻辑。本类仅覆盖 Claude 特有的配置项：
 * <ul>
 *   <li>提供程序名称 → {@code "Claude"}</li>
 *   <li>默认模型 → {@code "claude-sonnet-4-6"}</li>
 *   <li>桥接脚本资源路径 → {@code "bridge/claude-sdk-bridge.mjs"}</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * ClaudeSDKBridge bridge = new ClaudeSDKBridge();
 * bridge.setCallback((fn, args) -> callJavaScript(fn, args));
 * bridge.start(nodePath);
 * bridge.query(sessionId, prompt, cwd, model, permissionMode, reasoningEffort);
 * }</pre>
 *
 * @see BaseSDKBridge
 * @see com.protean.copilot.bridge.SdkBridge
 */
public class ClaudeSDKBridge extends BaseSDKBridge {

    // ==================== 抽象方法实现 ====================

    @Override
    protected String getProviderName() {
        return "Claude";
    }

    @Override
    protected String getDefaultModel() {
        return "claude-sonnet-4-6";
    }

    @Override
    protected String getBridgeScriptResource() {
        return "bridge/claude-sdk-bridge.mjs";
    }

    // ==================== 事件处理器覆写 ====================

    /**
     * 处理 Node.js 桥接就绪信号。
     * 在基类逻辑基础上，额外检查 Claude Code SDK 是否已安装。
     */
    @Override
    protected void handleReady(JsonObject message) {
        // 先执行基类的通用就绪处理（通知前端 SDK 版本等）
        super.handleReady(message);

        // Claude 特有：如果 SDK 不可用，输出安装提示
        boolean sdkAvailable = message.has("sdkAvailable")
            && message.get("sdkAvailable").getAsBoolean();

        if (!sdkAvailable) {
            log().warn("⚠️ Claude Code SDK 未安装或不可用！请运行: npm install -g @anthropic-ai/claude-code");
        }
    }
}
