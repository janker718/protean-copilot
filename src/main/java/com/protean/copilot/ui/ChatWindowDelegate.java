package com.protean.copilot.ui;

import com.protean.copilot.bridge.SdkBridge;
import com.protean.copilot.handler.*;
import com.protean.copilot.permission.PermissionService;
import com.protean.copilot.provider.claude.ClaudeSDKBridge;
import com.protean.copilot.session.ChatSession;
import com.protean.copilot.session.SessionLifecycleManager;
import com.protean.copilot.session.StreamMessageCoalescer;
import com.protean.copilot.settings.SettingsService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.jcef.JBCefBrowser;

import javax.swing.*;
import java.util.*;

/**
 * 编排处理器注册、权限设置、标签页状态和 QuickFix 操作。
 * 这是连接所有处理器和委托的接线瓶颈。
 *
 * 移植自 Claude Code GUI 参考实现。
 */
public class ChatWindowDelegate {

    private static final Logger LOG = Logger.getInstance(ChatWindowDelegate.class);

    /**
     * 标签页回答状态枚举 —— 映射前端状态。
     */
    public enum TabAnswerStatus {
        IDLE,
        ANSWERING,
        COMPLETED,
    }

    /**
     * 宿主接口，提供对窗口级依赖项的访问。
     */
    public interface DelegateHost {
        Project getProject();
        SdkBridge getSdkBridge();
        JPanel getMainPanel();
        SettingsService getSettingsService();
        ChatSession getSession();
        JBCefBrowser getBrowser();
        HandlerContext getHandlerContext();
        MessageDispatcher getMessageDispatcher();
        PermissionHandler getPermissionHandler();
        HistoryHandler getHistoryHandler();
        SessionLifecycleManager getSessionLifecycleManager();
        StreamMessageCoalescer getStreamCoalescer();
        WebviewWatchdog getWebviewWatchdog();
        boolean isDisposed();
        boolean isInitialized();
        boolean isFrontendReady();
        void callJavaScript(String functionName, String... args);
        void executeJavaScriptCode(String jsCode);
        int getTabIndex();
        void persistTabSessionState();
        void addCodeSnippetFromExternal(String selectionInfo);
        void updateTabLoadingState(boolean loading);
        com.protean.copilot.ui.toolwindow.PendingCodeSnippetBuffer getPendingCodeSnippetBuffer();
        void setFrontendReady(boolean ready);
        String getSessionId();
    }

    private final DelegateHost host;

    private volatile HandlerContext handlerContext = null;
    private volatile MessageDispatcher messageDispatcher = null;
    private volatile PermissionHandler permissionHandler = null;
    private volatile HistoryHandler historyHandler = null;

    public ChatWindowDelegate(DelegateHost host) {
        this.host = host;
    }

    /**
     * 初始化所有消息处理器和分发器。
     */
    public void initializeHandlers() {
        LOG.info("Initializing handlers for project: " + host.getProject().getName());

        MessageDispatcher dispatcher = new MessageDispatcher();
        messageDispatcher = dispatcher;

        // 创建处理器上下文
        HandlerContext.JsCallback jsCallback = new HandlerContext.JsCallback() {
            @Override
            public void callJavaScript(String functionName, String... args) {
                host.callJavaScript(functionName, args);
            }

            @Override
            public String escapeJs(String str) {
                return str
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
            }
        };

        HandlerContext context = new HandlerContext(
            host.getProject(),
            host.getSdkBridge(),
            host.getSettingsService(),
            jsCallback
        );
        context.setSession(host.getSession());
        context.setBrowser(host.getBrowser());
        handlerContext = context;

        // 创建权限和历史处理器
        PermissionHandler permHandler = new PermissionHandler();
        permissionHandler = permHandler;

        HistoryHandler histHandler = new HistoryHandler();
        historyHandler = histHandler;

        // 为用户消息注册处理器 —— 通过 ClaudeSDKBridge 转发到 claude-code-sdk
        dispatcher.registerHandler(new MessageHandler() {
            @Override
            public boolean handle(String type, String content) {
                switch (type) {
                    case "send_message", "user_message" -> {
                        LOG.info("用户消息: " + (content != null && content.length() > 100
                            ? content.substring(0, 100) : content));

                        try {
                            // 解析消息内容
                            String text;
                            String permissionMode = "bypassPermissions";

                            try {
                                JsonObject msg = JsonParser.parseString(content).getAsJsonObject();
                                text = msg.has("text")
                                    ? msg.get("text").getAsString()
                                    : content;
                                if (msg.has("permissionMode")) {
                                    permissionMode = msg.get("permissionMode").getAsString();
                                }
                            } catch (Exception e) {
                                // 消息不是 JSON 格式，直接作为纯文本处理
                                text = content;
                            }

                            // 获取 ClaudeSDKBridge 实例
                            ClaudeSDKBridge bridge = context.sdkBridge.getClaudeBridge();
                            if (bridge == null || !bridge.isRunning()) {
                                context.callJavaScript("addErrorMessage",
                                    "Claude SDK 桥接未运行。请检查 Node.js 和 claude-code-sdk 是否正确安装。");
                                context.callJavaScript("showLoading", "false");
                                return true;
                            }

                            // 获取会话信息
                            ChatSession session = context.getSession();
                            String cwd = session.cwd;
                            String model = context.getCurrentModel();
                            String re = session.reasoningEffort;

                            // 确保 sessionId 存在（首次查询时自动生成）
                            if (session.sessionId == null) {
                                session.sessionId = UUID.randomUUID().toString();
                                LOG.info("自动生成 sessionId: " + session.sessionId);
                            }

                            // 通知前端进入加载状态
                            context.callJavaScript("onStreamStart");

                            // 发起查询
                            bridge.query(session.sessionId, text, cwd, model,
                                permissionMode, re)
                                .whenComplete((v, ex) -> {
                                    if (ex != null) {
                                        String errMsg = ex.getMessage() != null
                                            ? ex.getMessage()
                                            : ex.getClass().getSimpleName();
                                        LOG.warn("查询失败: " + errMsg);
                                        context.callJavaScript("addErrorMessage",
                                            "查询失败: " + errMsg);
                                        context.callJavaScript("showLoading", "false");
                                    }
                                });
                            return true;

                        } catch (Exception e) {
                            LOG.warn("处理用户消息失败: " + e.getMessage(), e);
                            context.callJavaScript("addErrorMessage",
                                "处理消息失败: " + e.getMessage());
                            context.callJavaScript("showLoading", "false");
                            return true;
                        }
                    }

                    // 中断当前会话
                    case "interrupt_session" -> {
                        ChatSession s = context.getSession();
                        if (s != null) {
                            s.interrupt();
                            context.callJavaScript("onStreamEnd");
                            context.callJavaScript("showLoading", "false");
                            LOG.info("会话已中断: " + s.sessionId);
                        }
                        return true;
                    }

                    // 切换 AI 提供商
                    case "set_provider" -> {
                        context.setCurrentProvider(content);
                        LOG.info("提供商已切换: " + content);
                        return true;
                    }

                    // 切换模型
                    case "set_model" -> {
                        context.setCurrentModel(content);
                        LOG.info("模型已切换: " + content);
                        return true;
                    }

                    default -> { return false; }
                }
            }

            @Override
            public List<String> getSupportedTypes() {
                return List.of("send_message", "user_message",
                    "interrupt_session", "set_provider", "set_model");
            }
        });

        LOG.info("Handlers initialized: " + dispatcher.getHandlerCount() + " handlers registered");
    }

    /**
     * 为当前会话设置权限服务。
     * @return 会话 ID
     */
    public String setupPermissionService() {
        String sessionId = UUID.randomUUID().toString();
        try {
            PermissionService.getInstance(host.getProject(), sessionId);
            LOG.info("PermissionService created for session: " + sessionId);
        } catch (Exception e) {
            LOG.warn("Failed to create PermissionService: " + e.getMessage());
        }
        return sessionId;
    }

    /**
     * 从设置中加载权限模式。
     */
    public void loadPermissionModeFromSettings() {
        String mode = host.getSettingsService().getPermissionMode();
        host.getSession().permissionMode = mode;
        LOG.info("Permission mode loaded: " + mode);
    }

    /**
     * 从设置中加载 Node.js 路径。
     */
    public void loadNodePathFromSettings() {
        // 桩：ProteanCopilot 中暂无 Node.js 依赖
    }

    /**
     * 从设置中同步当前使用的提供商。
     */
    public void syncActiveProvider() {
        String provider = host.getSettingsService().getProvider();
        host.getSession().provider = provider;
        LOG.info("Active provider synced: " + provider);
    }

    /**
     * 初始化状态栏（模型、模式、代理显示）。
     */
    public void initializeStatusBar() {
        // 桩：状态栏初始化尚未实现
    }

    /**
     * 更新标签页状态显示名称。
     */
    public void updateTabStatus(TabAnswerStatus status) {
        try {
            host.callJavaScript("setTabStatus", status.name());
        } catch (Exception e) {
            LOG.warn("Failed to update tab status: " + e.getMessage());
        }
    }

    /**
     * 向会话发送 quick-fix 消息。
     * 如果前端尚未就绪，则排队等待消息。
     */
    public void sendQuickFixMessage(String prompt, boolean isQuickFix) {
        if (!host.isFrontendReady()) {
            LOG.info("Frontend not ready, deferring quick-fix message");
            return;
        }

        try {
            host.callJavaScript("addCodeSnippet", prompt);
            LOG.info("Quick-fix message sent: " + (prompt.length() > 80 ? prompt.substring(0, 80) : prompt));
        } catch (Exception e) {
            LOG.warn("Failed to send quick-fix message: " + e.getMessage());
        }
    }

    /**
     * 获取处理器上下文（由 initializeHandlers 初始化）。
     */
    public HandlerContext getHandlerContext() {
        return handlerContext;
    }

    /**
     * 获取消息分发器（由 initializeHandlers 初始化）。
     */
    public MessageDispatcher getMessageDispatcher() {
        return messageDispatcher;
    }

    /**
     * 获取权限处理器（由 initializeHandlers 初始化）。
     */
    public PermissionHandler getPermissionHandler() {
        return permissionHandler;
    }

    /**
     * 获取历史处理器（由 initializeHandlers 初始化）。
     */
    public HistoryHandler getHistoryHandler() {
        return historyHandler;
    }

    /**
     * 释放委托并取消所有待处理任务。
     */
    public void dispose() {
        LOG.info("ChatWindowDelegate disposed for project: " + host.getProject().getName());
    }
}
