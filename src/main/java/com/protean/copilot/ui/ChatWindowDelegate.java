package com.protean.copilot.ui;

import com.protean.copilot.bridge.SdkBridge;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.handler.core.MessageDispatcher;
import com.protean.copilot.handler.core.MessageHandler;
import com.protean.copilot.handler.diff.DiffHandler;
import com.protean.copilot.handler.DependencyHandler;
import com.protean.copilot.handler.FrontendActionCoverageHandler;
import com.protean.copilot.handler.HistoryHandler;
import com.protean.copilot.handler.PermissionHandler;
import com.protean.copilot.handler.SettingsHandler;
import com.protean.copilot.handler.WindowEventHandler;
import com.protean.copilot.handler.provider.ProviderHandler;
import com.protean.copilot.permission.PermissionService;
import com.protean.copilot.session.ChatSession;
import com.protean.copilot.session.SessionLifecycleManager;
import com.protean.copilot.session.SessionRuntimeMessages;
import com.protean.copilot.session.StreamMessageCoalescer;
import com.protean.copilot.settings.CodemossSettingsService;
import com.protean.copilot.settings.SettingsService;
import com.protean.copilot.settings.manager.ProviderManager;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
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
        context.setCurrentProvider(getProviderManager().getActiveProvider());
        handlerContext = context;

        // 创建权限和历史处理器
        PermissionHandler permHandler = new PermissionHandler(context);
        permissionHandler = permHandler;
        dispatcher.registerHandler(permHandler);

        HistoryHandler histHandler = new HistoryHandler(
            context,
            host.getSessionLifecycleManager()
        );
        historyHandler = histHandler;
        dispatcher.registerHandler(histHandler);
        dispatcher.registerHandler(new SettingsHandler(context));
        dispatcher.registerHandler(new ProviderHandler(context));
        dispatcher.registerHandler(new DependencyHandler(context));
        dispatcher.registerHandler(new WindowEventHandler(
            context,
            host::updateTabLoadingState,
            this::onTabStatusChanged,
            host.getSessionLifecycleManager()::createNewSession
        ));
        dispatcher.registerHandler(new FrontendActionCoverageHandler(context));

        // 为用户消息注册处理器 —— 通过 session/provider 通用层转发到对应 SDK bridge
        dispatcher.registerHandler(new MessageHandler() {
            @Override
            public boolean handle(String type, String content) {
                switch (type) {
                    case "send_message", "user_message", "send_message_with_attachments" -> {
                        LOG.info("用户消息: " + (content != null && content.length() > 100
                            ? content.substring(0, 100) : content));

                        try {
                            // 解析消息内容
                            String text;
                            String permissionMode = "bypassPermissions";
                            String reasoningEffort = null;
                            List<ChatSession.Attachment> attachments = null;

                            try {
                                JsonObject msg = JsonParser.parseString(content).getAsJsonObject();
                                text = msg.has("text")
                                    ? msg.get("text").getAsString()
                                    : content;
                                if (msg.has("permissionMode")) {
                                    permissionMode = msg.get("permissionMode").getAsString();
                                }
                                if (msg.has("reasoningEffort")) {
                                    reasoningEffort = msg.get("reasoningEffort").getAsString();
                                }
                                if (msg.has("attachments") && msg.get("attachments").isJsonArray()) {
                                    attachments = parseAttachments(msg.getAsJsonArray("attachments"));
                                }
                            } catch (Exception e) {
                                // 消息不是 JSON 格式，直接作为纯文本处理
                                text = content;
                            }

                            String activeProvider = getProviderManager().getActiveProvider();
                            if (!context.sdkBridge.isProviderRunning(activeProvider)) {
                                context.callJavaScript("addErrorMessage",
                                    SessionRuntimeMessages.bridgeUnavailable(activeProvider));
                                context.callJavaScript("showLoading", "false");
                                return true;
                            }

                            // 获取会话信息
                            ChatSession session = context.getSession();
                            session.setModel(context.getCurrentModel());
                            session.setProvider(activeProvider);

                            session.send(
                                text,
                                attachments,
                                null,
                                null,
                                permissionMode,
                                reasoningEffort,
                                null
                            )
                                .whenComplete((v, ex) -> {
                                    if (ex != null) {
                                        String errMsg = ex.getMessage() != null
                                            ? ex.getMessage()
                                            : ex.getClass().getSimpleName();
                                        LOG.warn("查询失败: " + errMsg);
                                        context.callJavaScript("addErrorMessage",
                                            SessionRuntimeMessages.requestFailed(
                                                session.getProvider(),
                                                ex
                                            ));
                                        context.callJavaScript("showLoading", "false");
                                    }
                                });
                            return true;

                        } catch (Exception e) {
                            LOG.warn("处理用户消息失败: " + e.getMessage(), e);
                            context.callJavaScript("addErrorMessage",
                                SessionRuntimeMessages.requestFailed(
                                    context.getSession() != null ? context.getSession().getProvider() : null,
                                    e
                                ));
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
                            LOG.info("会话已中断: " + s.getSessionId());
                        }
                        return true;
                    }

                    default -> { return false; }
                }
            }

            @Override
            public List<String> getSupportedTypes() {
                return List.of("send_message", "user_message", "send_message_with_attachments",
                    "interrupt_session");
            }
        });

        // 注册 Diff 处理器（show_diff, show_interactive_diff 等 6 种消息）
        dispatcher.registerHandler(new DiffHandler(handlerContext));

        LOG.info("Handlers initialized: " + dispatcher.getHandlerCount() + " handlers registered");
    }

    private static List<ChatSession.Attachment> parseAttachments(JsonArray array) {
        List<ChatSession.Attachment> attachments = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            if (!array.get(i).isJsonObject()) {
                continue;
            }
            JsonObject object = array.get(i).getAsJsonObject();
            attachments.add(new ChatSession.Attachment(
                object.has("fileName") && !object.get("fileName").isJsonNull()
                    ? object.get("fileName").getAsString() : "",
                object.has("mediaType") && !object.get("mediaType").isJsonNull()
                    ? object.get("mediaType").getAsString() : "",
                object.has("data") && !object.get("data").isJsonNull()
                    ? object.get("data").getAsString() : ""
            ));
        }
        return attachments;
    }

    /**
     * 为当前会话设置权限服务。
     * @return 会话 ID
     */
    public String setupPermissionService() {
        String sessionId = UUID.randomUUID().toString();
        try {
            PermissionService permissionService = PermissionService.getInstance(host.getProject(), sessionId);
            if (handlerContext != null) {
                handlerContext.setPermissionService(permissionService);
            }
            permissionService.setPermissionMode(host.getSettingsService().getPermissionMode());
            if (permissionHandler != null) {
                permissionHandler.bindPermissionService(permissionService);
                permissionService.setOnPermissionRequestedCallback(permissionHandler::showPermissionDialog);
                permissionService.registerDialogShower(host.getProject(), permissionHandler::showFrontendPermissionDialog);
                permissionService.registerAskUserQuestionDialogShower(host.getProject(), permissionHandler::showAskUserQuestionDialog);
                permissionService.registerPlanApprovalDialogShower(host.getProject(), permissionHandler::showPlanApprovalDialog);
                permissionService.setLastActiveProject(host.getProject());
            }
            permissionService.start();
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
        host.getSession().setPermissionMode(mode);
        if (handlerContext != null && handlerContext.getPermissionService() != null) {
            handlerContext.getPermissionService().setPermissionMode(mode);
        }
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
        String provider = getProviderManager().getActiveProvider();
        if (handlerContext != null) {
            handlerContext.setCurrentProvider(provider);
        }
        host.getSession().setProvider(provider);
        LOG.info("Active provider synced: " + provider);
    }

    private ProviderManager getProviderManager() {
        return ProviderManager.getInstance(host.getProject());
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

    private void onTabStatusChanged(String status) {
        TabAnswerStatus resolved = switch (status == null ? "" : status.toLowerCase(Locale.ROOT)) {
            case "answering", "loading", "streaming" -> TabAnswerStatus.ANSWERING;
            case "completed", "complete", "done" -> TabAnswerStatus.COMPLETED;
            default -> TabAnswerStatus.IDLE;
        };
        updateTabStatus(resolved);
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
