package com.protean.copilot.bridge;

import com.google.gson.JsonObject;
import com.protean.copilot.provider.claude.ClaudeSDKBridge;
import com.protean.copilot.provider.codex.CodexSDKBridge;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一的 SDK 桥接接口。
 * 替代参考实现中独立的 ClaudeSDKBridge 和 CodexSDKBridge。
 * 持有各提供商的桥接实例并统一管理进程生命周期。
 *
 * <p>当前支持的提供商：
 * <ul>
 *   <li><b>Claude</b> — 通过 {@link ClaudeSDKBridge} 对接 npm claude-code-sdk</li>
 *   <li><b>Codex</b> — 通过 {@link CodexSDKBridge} 对接 Codex 运行时桥接</li>
 * </ul>
 */
public class SdkBridge {

    /**
     * 守护进程事件监听器的函数式接口。
     * 用于接收来自 SDK 后端的异步事件通知。
     */
    @FunctionalInterface
    public interface DaemonEventListener {
        /**
         * 当守护进程事件发生时调用。
         *
         * @param event 事件类型标识符
         * @param data  事件携带的数据
         */
        void onEvent(String event, JsonObject data);
    }

    /** 守护进程事件监听器列表 */
    private final List<DaemonEventListener> daemonListeners = new ArrayList<>();

    /** Claude SDK 桥接实例 */
    private volatile ClaudeSDKBridge claudeBridge;
    /** Codex SDK 桥接实例 */
    private volatile CodexSDKBridge codexBridge;

    /**
     * 设置 Claude SDK 桥接实例。
     * 由 ProteanChatWindow 在初始化时注入。
     *
     * @param bridge ClaudeSDKBridge 实例
     */
    public void setClaudeBridge(ClaudeSDKBridge bridge) {
        this.claudeBridge = bridge;
    }

    /**
     * 获取 Claude SDK 桥接实例。
     *
     * @return ClaudeSDKBridge 实例，如果尚未设置则返回 null
     */
    public ClaudeSDKBridge getClaudeBridge() {
        return claudeBridge;
    }

    public void setCodexBridge(CodexSDKBridge bridge) {
        this.codexBridge = bridge;
    }

    public CodexSDKBridge getCodexBridge() {
        return codexBridge;
    }

    public boolean isProviderRunning(String provider) {
        if (provider == null || provider.isBlank()) {
            return claudeBridge != null && claudeBridge.isRunning();
        }
        String normalized = provider.trim().toLowerCase();
        return switch (normalized) {
            case "claude" -> claudeBridge != null && claudeBridge.isRunning();
            case "codex" -> codexBridge != null && codexBridge.isRunning();
            default -> false;
        };
    }

    /**
     * 获取活跃的 SDK 进程数量。
     *
     * @return 当前运行中的 SDK 进程数
     */
    public int getActiveProcessCount() {
        int count = 0;
        if (claudeBridge != null && claudeBridge.isRunning()) {
            count++;
        }
        if (codexBridge != null && codexBridge.isRunning()) {
            count++;
        }
        return count;
    }

    /**
     * 清理所有 SDK 进程。
     * 在窗口释放时调用，确保所有子进程被正确终止。
     */
    public void cleanupAllProcesses() {
        if (claudeBridge != null) {
            claudeBridge.shutdown();
        }
        if (codexBridge != null) {
            codexBridge.shutdown();
        }
    }

    /**
     * 添加守护进程事件监听器。
     *
     * @param listener 事件监听器
     */
    public void addDaemonEventListener(DaemonEventListener listener) {
        daemonListeners.add(listener);
    }

    /**
     * 移除守护进程事件监听器。
     *
     * @param listener 要移除的事件监听器
     */
    public void removeDaemonEventListener(DaemonEventListener listener) {
        daemonListeners.remove(listener);
    }

    /**
     * 获取当前提供程序名称。
     *
     * @return 提供程序名称（如 "claude"）
     */
    public String getProvider() {
        if (claudeBridge != null && claudeBridge.isRunning()) {
            return "claude";
        }
        if (codexBridge != null && codexBridge.isRunning()) {
            return "codex";
        }
        return "protean";
    }
}
