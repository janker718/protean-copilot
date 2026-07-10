package com.protean.copilot.provider.claude;

import com.google.gson.JsonObject;
import com.protean.copilot.bridge.NodeDetector;
import com.protean.copilot.provider.common.BaseSDKBridge;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Claude SDK 桥接 —— 对接 npm {@code @anthropic-ai/claude-agent-sdk} 的具体实现。
 *
 * <p>继承自 {@link BaseSDKBridge}，复用所有通用的进程管理、JSON-line 协议、
 * 会话生命周期和流式事件处理逻辑。本类提供 Claude 特有的公共 API：
 *
 * <ul>
 *   <li>提供程序配置（名称、默认模型、桥接脚本路径）</li>
 *   <li>Daemon 便捷方法（prewarmDaemonAsync、shutdownDaemon）</li>
 *   <li>Node.js 环境检测</li>
 *   <li>上下文用量查询</li>
 *   <li>资源清理（cleanupAllProcesses）</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * ClaudeSDKBridge bridge = new ClaudeSDKBridge();
 * bridge.setCallback((fn, args) -> callJavaScript(fn, args));
 * bridge.start(nodePath);
 * bridge.prewarmDaemonAsync(cwd);
 * bridge.query(sessionId, prompt, cwd, model, permissionMode, reasoningEffort);
 * }</pre>
 *
 * @see BaseSDKBridge
 * @see com.protean.copilot.bridge.SdkBridge
 */
public class ClaudeSDKBridge extends BaseSDKBridge {

    private final NodeDetector nodeDetector = NodeDetector.getInstance();

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

    @Override
    protected Map<String, String> getBridgeEnvironment() {
        Path managedNodeModules = Path.of(
            System.getProperty("user.home"), ".codemoss", "dependencies", "claude-sdk", "node_modules"
        );
        return Map.of("PROTEAN_CLAUDE_SDK_NODE_MODULES", managedNodeModules.toString());
    }

    // ==================== Daemon 便捷方法 ====================

    /**
     * 异步预加载 Daemon SDK。
     * @param cwd 工作目录（项目根路径）
     */
    public void prewarmDaemonAsync(String cwd) {
        prewarm(cwd, null, null)
            .thenAccept(v -> log().info("Claude SDK prewarm 完成"))
            .exceptionally(ex -> {
                log().warn("Claude SDK prewarm 失败: " + ex.getMessage());
                return null;
            });
    }

    /**
     * 异步预加载 Daemon SDK（带 session epoch）。
     * @param cwd 工作目录
     * @param runtimeSessionEpoch session epoch（用于运行时缓存失效）
     */
    public void prewarmDaemonAsync(String cwd, String runtimeSessionEpoch) {
        prewarm(cwd, null, null)
            .thenAccept(v -> log().info("Claude SDK prewarm 完成 (epoch=" + runtimeSessionEpoch + ")"))
            .exceptionally(ex -> {
                log().warn("Claude SDK prewarm 失败: " + ex.getMessage());
                return null;
            });
    }

    /**
     * 异步预加载 Daemon SDK（历史会话恢复）。
     * @param cwd 工作目录
     * @param runtimeSessionEpoch session epoch
     * @param sessionId 历史会话 ID（用于 --resume）
     */
    public void prewarmDaemonAsync(String cwd, String runtimeSessionEpoch, String sessionId) {
        prewarm(cwd, null, null)
            .thenAccept(v -> log().info("Claude SDK prewarm 完成 (session=" + sessionId + ")"))
            .exceptionally(ex -> {
                log().warn("Claude SDK prewarm 失败: " + ex.getMessage());
                return null;
            });
    }

    /**
     * 关闭 Daemon 进程。
     */
    public void shutdownDaemon() {
        shutdown();
    }

    /**
     * 清理所有进程（Daemon 关闭 + 残留子进程清理）。
     * SdkBridge 通过此方法在窗口释放时统一清理资源。
     */
    public void cleanupAllProcesses() {
        shutdownDaemon();
    }

    // ==================== Node.js 环境检测 ====================

    /** 委托给共享的 NodeDetector 单例。 */
    public NodeDetectionResult detectNodeWithDetails() {
        return nodeDetector.detectNodeWithDetails();
    }

    public NodeDetectionResult verifyNodePath(String path) {
        return nodeDetector.verifyNodePath(path);
    }

    public NodeDetectionResult verifyAndCacheNodePath(String path) {
        return nodeDetector.verifyAndCacheNodePath(path);
    }

    public String getCachedNodeVersion() {
        return nodeDetector.getCachedNodeVersion();
    }

    public String getCachedNodePath() {
        return nodeDetector.getCachedNodePath();
    }

    public void clearNodeCache() {
        nodeDetector.clearCache();
    }

    // ==================== 上下文用量查询 ====================

    /**
     * 获取上下文窗口用量。
     * 当前为桩实现，后续通过 Daemon 模式获取 SDK 真实的 getContextUsage()。
     *
     * @param sessionId 会话 ID
     * @param cwd 工作目录
     * @param model 模型 ID
     * @return 包含上下文用量的 CompletableFuture
     */
    public CompletableFuture<JsonObject> getContextUsage(String sessionId, String cwd, String model) {
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        // 桩：后续通过 daemon 调用 claude.getContextUsage
        JsonObject result = new JsonObject();
        result.addProperty("success", false);
        result.addProperty("error", "getContextUsage requires daemon mode (not yet implemented)");
        if (sessionId != null) result.addProperty("sessionId", sessionId);
        future.complete(result);
        return future;
    }

    // ==================== 事件处理器覆写 ====================

    /**
     * 处理 Node.js 桥接就绪信号。
     * 在基类通用逻辑基础上，添加 Claude 特有的 SDK 可用性诊断。
     */
    @Override
    protected void handleReady(JsonObject message) {
        // 先执行基类的通用就绪处理（通知前端 SDK 版本等）
        super.handleReady(message);

        // Claude 特有诊断信息
        String version = message.has("version") ? message.get("version").getAsString() : "unknown";
        boolean sdkAvailable = message.has("sdkAvailable")
            && message.get("sdkAvailable").getAsBoolean();

        if (sdkAvailable) {
            log().info("✓ Claude Agent SDK 就绪: version=" + version
                + ", model=" + getDefaultModel());
        } else {
            log().warn("⚠ Claude Agent SDK 未安装或不可用！");
            log().warn("  请在 Settings 的 SDK 依赖管理中重新安装 Claude Code SDK");
        }
    }

    // ==================== 覆盖：添加默认字段 ====================

    @Override
    public CompletableFuture<Void> query(
            String sessionId, String prompt, String cwd,
            String model, String permissionMode, String reasoningEffort
    ) {
        // 确保 Claude 特有的默认值
        if (model == null || model.isEmpty()) {
            model = getDefaultModel();
        }
        if (permissionMode == null || permissionMode.isEmpty()) {
            permissionMode = "default";
        }
        return super.query(sessionId, prompt, cwd, model, permissionMode, reasoningEffort);
    }
}
