package com.protean.copilot.ui.toolwindow;

import com.protean.copilot.bridge.SdkBridge;
import com.protean.copilot.handler.*;
import com.protean.copilot.permission.PermissionService;
import com.protean.copilot.provider.claude.ClaudeSDKBridge;
import com.protean.copilot.session.*;
import com.protean.copilot.settings.SettingsService;
import com.protean.copilot.settings.TabStateService;
import com.protean.copilot.ui.*;
import com.protean.copilot.util.HtmlLoader;
import com.protean.copilot.util.JsUtils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.content.Content;
import com.intellij.ui.jcef.JBCefBrowser;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Protean Copilot 的主聊天窗口。使用 JCEF（Chromium WebView）渲染聊天界面，
 * 并协调所有会话、处理器和 UI 生命周期。
 *
 * 架构移植自 jetbrains-cc-gui 项目中的 ClaudeChatWindow.java。
 *
 * 关键设计模式：
 * - **宿主接口模式**：匿名对象表达式实现宿主接口
 *   （WebviewHost、DelegateHost、SessionHost），仅向每个委托暴露所需的方法。
 * - **延迟初始化**：JCEF 浏览器在构造函数之后通过 ToolWindowManager.invokeLater()
 *   创建，以避免类加载死锁。
 * - **前端就绪**：PendingCodeSnippetBuffer 保存在前端发出就绪信号之前到达的代码片段。
 * - **安全的 JS 桥接**：callJavaScript 验证函数名并始终在 EDT 上运行。
 * - **全面释放**：同步、幂等、每一步独立的 try/catch。
 */
public class ProteanChatWindow {

    private static final Logger LOG = Logger.getInstance(ProteanChatWindow.class);

    // ===== 核心依赖 =====

    private final Project project;
    private final JPanel mainPanel;
    private final SdkBridge sdkBridge;
    private final SettingsService settingsService;
    private final HtmlLoader htmlLoader;

    // ===== 会话和委托状态 =====

    private Content parentContent = null;
    private String originalTabName = null;

    private volatile String sessionId = null;
    private volatile JBCefBrowser browser = null;
    private volatile ChatSession session;
    private volatile boolean disposed = false;
    private volatile boolean initialized = false;
    private volatile boolean frontendReady = false;

    private final PendingCodeSnippetBuffer pendingCodeSnippetBuffer;

    private volatile boolean slashCommandsFetched = false;
    private final AtomicBoolean restoredHistoryLoadStarted = new AtomicBoolean(false);
    private volatile int fetchedSlashCommandsCount = 0;

    // ===== 处理器和分发器 =====

    private volatile HandlerContext handlerContext = null;
    private volatile MessageDispatcher messageDispatcher = null;
    private volatile PermissionHandler permissionHandler = null;
    private volatile HistoryHandler historyHandler = null;

    // ===== 委托 =====

    private WebviewInitializer webviewInitializer;
    private EditorContextTracker editorContextTracker;
    private ChatWindowDelegate chatWindowDelegate;
    private SessionLifecycleManager sessionLifecycleManager;
    private SessionCallbackAdapter sessionCallbackAdapter = null;

    // ===== 组合对象 =====

    private final StreamMessageCoalescer streamCoalescer;
    private final WebviewWatchdog webviewWatchdog;

    // ===== 标题事件监听器 =====

    private volatile SdkBridge.DaemonEventListener titleEventListener = null;

    // ===== 构造函数（原 Kotlin init 块） =====

    public ProteanChatWindow(Project project, boolean skipRegister) {
        this.project = project;
        this.mainPanel = new JPanel(new BorderLayout());
        this.sdkBridge = new SdkBridge();
        this.settingsService = new SettingsService();
        this.htmlLoader = new HtmlLoader(ProteanChatWindow.class);
        this.pendingCodeSnippetBuffer = new PendingCodeSnippetBuffer();

        // ===== 初始化 Claude SDK 桥接 =====
        // 创建 ClaudeSDKBridge 实例并注入到 SdkBridge
        ClaudeSDKBridge claudeBridge = new ClaudeSDKBridge();
        claudeBridge.setCallback(new ClaudeSDKBridge.BridgeCallback() {
            @Override
            public void callJavaScript(String functionName, String... args) {
                ProteanChatWindow.this.callJavaScript(functionName, args);
            }
        });
        sdkBridge.setClaudeBridge(claudeBridge);

        // 异步启动 Node.js 桥接进程（避免阻塞 EDT 和构造函数）
        // 基类 BaseSDKBridge 通过 getBridgeScriptResource() 自动提取脚本
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                String nodePath = settingsService.getNodePath();
                claudeBridge.start(nodePath);
                LOG.info("Claude SDK 桥接启动成功");

                // 启动后立即预加载 SDK，消除首次查询的 3-5 秒冷启动延迟
                claudeBridge.prewarm(
                    project.getBasePath(), null, null
                ).thenAccept(v ->
                    LOG.info("Claude SDK prewarm 完成")
                ).exceptionally(ex -> {
                    LOG.warn("Claude SDK prewarm 失败（将在首次查询时加载）: "
                        + ex.getMessage());
                    return null;
                });
            } catch (Exception e) {
                LOG.error("启动 Claude SDK 桥接失败: " + e.getMessage(), e);
                ApplicationManager.getApplication().invokeLater(() -> {
                    callJavaScript("addErrorMessage",
                        "启动 Claude SDK 桥接失败: " + e.getMessage());
                });
            }
        });

        // 1. 创建带有 JsCallbackTarget 的 StreamMessageCoalescer
        streamCoalescer = new StreamMessageCoalescer(new StreamMessageCoalescer.JsCallbackTarget() {
            @Override
            public void callJavaScript(String functionName, String... args) {
                ProteanChatWindow.this.callJavaScript(functionName, args);
            }

            @Override
            public JBCefBrowser getBrowser() {
                return browser;
            }

            @Override
            public boolean isDisposed() {
                return disposed;
            }

            @Override
            public HandlerContext getHandlerContext() {
                if (handlerContext == null) {
                    throw new IllegalStateException("HandlerContext not initialized");
                }
                return handlerContext;
            }
        });

        // 2. 创建 WebviewWatchdog
        webviewWatchdog = new WebviewWatchdog(
            mainPanel,
            () -> browser,
            htmlLoader,
            reason -> {
                if (!disposed) {
                    webviewInitializer.recreateWebview(reason);
                }
            },
            () -> disposed,
            () -> streamCoalescer.isStreamActive()
        );

        // 3. 创建会话
        session = new ChatSession(project, sdkBridge);

        // 4. 创建 ChatWindowDelegate
        chatWindowDelegate = new ChatWindowDelegate(createDelegateHost());
        chatWindowDelegate.loadPermissionModeFromSettings();
        chatWindowDelegate.loadNodePathFromSettings();
        chatWindowDelegate.syncActiveProvider();

        // 5. 初始化处理器
        chatWindowDelegate.initializeHandlers();
        handlerContext = chatWindowDelegate.getHandlerContext();
        messageDispatcher = chatWindowDelegate.getMessageDispatcher();
        permissionHandler = chatWindowDelegate.getPermissionHandler();
        historyHandler = chatWindowDelegate.getHistoryHandler();

        // 在处理器上下文上设置浏览器和会话
        if (handlerContext != null) {
            handlerContext.setBrowser(browser);
            handlerContext.setSession(session);
        }

        // 6. 设置权限服务
        sessionId = chatWindowDelegate.setupPermissionService();

        // 7. 使用内联 SessionHost 创建 SessionLifecycleManager
        sessionLifecycleManager = new SessionLifecycleManager(new SessionLifecycleManager.SessionHost() {
            @Override
            public Project getProject() {
                return project;
            }

            @Override
            public ChatSession getSession() {
                return session;
            }

            @Override
            public void setSession(ChatSession s) {
                session = s;
            }

            @Override
            public HandlerContext getHandlerContext() {
                if (handlerContext == null) {
                    throw new IllegalStateException("HandlerContext not initialized");
                }
                return handlerContext;
            }

            @Override
            public StreamMessageCoalescer getStreamCoalescer() {
                return streamCoalescer;
            }

            @Override
            public void clearPendingPermissionRequests() {
                if (permissionHandler != null) {
                    permissionHandler.clearPendingRequests();
                }
            }

            @Override
            public void clearPermissionDecisionMemory() {
                try {
                    PermissionService.getInstance(project, sessionId != null ? sessionId : "").clearDecisionMemory();
                } catch (Exception ignored) {
                }
            }

            @Override
            public void callJavaScript(String functionName, String... args) {
                ProteanChatWindow.this.callJavaScript(functionName, args);
            }

            @Override
            public boolean isDisposed() {
                return disposed;
            }

            @Override
            public JBCefBrowser getBrowser() {
                return browser;
            }

            @Override
            public void setupSessionCallbacks() {
                ProteanChatWindow.this.setupSessionCallbacks();
            }

            @Override
            public void invalidateSessionCallbacks() {
                if (sessionCallbackAdapter != null) {
                    sessionCallbackAdapter.dispose();
                }
                sessionCallbackAdapter = null;
            }

            @Override
            public void setSlashCommandsFetched(boolean fetched) {
                slashCommandsFetched = fetched;
            }

            @Override
            public void setFetchedSlashCommandsCount(int count) {
                fetchedSlashCommandsCount = count;
            }
        });

        // 8. 创建 EditorContextTracker
        editorContextTracker = new EditorContextTracker(
            project,
            new EditorContextTracker.ContextCallback() {
                @Override
                public void addSelectionInfo(String info) {
                    callJavaScript("addSelectionInfo", JsUtils.escapeJs(info));
                }

                @Override
                public void clearSelectionInfo() {
                    callJavaScript("clearSelectionInfo");
                }
            }
        );
        editorContextTracker.registerListeners();

        // 9. 创建 WebviewInitializer
        webviewInitializer = new WebviewInitializer(createWebviewHost());

        // 10. 设置会话回调和初始信息
        setupSessionCallbacks();
        initializeSessionInfo();

        // 11. 延迟创建浏览器，以避免 JCEF 初始化冲突
        ToolWindowManager.getInstance(project).invokeLater(() -> {
            if (!disposed) {
                webviewInitializer.createUIComponents();
                registerSessionLoadListener();
                initialized = true;
                LOG.info("Window instance fully initialized, project: " + project.getName());
            }
        });

        // 12. 注册实例并初始化状态栏
        if (!skipRegister) {
            registerInstance();
        }
        chatWindowDelegate.initializeStatusBar();
    }

    /** Convenience constructor used when skipRegister defaults to false. */
    public ProteanChatWindow(Project project) {
        this(project, false);
    }

    // ===== 宿主接口工厂 =====

    private WebviewInitializer.WebviewHost createWebviewHost() {
        return new WebviewInitializer.WebviewHost() {
            @Override
            public Project getProject() {
                return project;
            }

            @Override
            public JPanel getMainPanel() {
                return mainPanel;
            }

            @Override
            public HtmlLoader getHtmlLoader() {
                return htmlLoader;
            }

            @Override
            public JBCefBrowser getBrowser() {
                return browser;
            }

            @Override
            public void setBrowser(JBCefBrowser b) {
                browser = b;
                if (handlerContext != null) {
                    handlerContext.setBrowser(b);
                }
            }

            @Override
            public boolean isDisposed() {
                return disposed;
            }

            @Override
            public WebviewWatchdog getWebviewWatchdog() {
                return webviewWatchdog;
            }

            @Override
            public void setFrontendReady(boolean ready) {
                frontendReady = ready;
                if (ready) {
                    flushPendingCodeSnippet();
                }
            }

            @Override
            public void handleJavaScriptMessage(String message) {
                ProteanChatWindow.this.handleJavaScriptMessage(message);
            }
        };
    }

    private ChatWindowDelegate.DelegateHost createDelegateHost() {
        return new ChatWindowDelegate.DelegateHost() {
            @Override
            public Project getProject() {
                return project;
            }

            @Override
            public SdkBridge getSdkBridge() {
                return sdkBridge;
            }

            @Override
            public JPanel getMainPanel() {
                return mainPanel;
            }

            @Override
            public SettingsService getSettingsService() {
                return settingsService;
            }

            @Override
            public ChatSession getSession() {
                return session;
            }

            @Override
            public JBCefBrowser getBrowser() {
                return browser;
            }

            @Override
            public HandlerContext getHandlerContext() {
                if (handlerContext == null) {
                    throw new IllegalStateException("HandlerContext not initialized");
                }
                return handlerContext;
            }

            @Override
            public MessageDispatcher getMessageDispatcher() {
                if (messageDispatcher == null) {
                    throw new IllegalStateException("MessageDispatcher not initialized");
                }
                return messageDispatcher;
            }

            @Override
            public PermissionHandler getPermissionHandler() {
                if (permissionHandler == null) {
                    throw new IllegalStateException("PermissionHandler not initialized");
                }
                return permissionHandler;
            }

            @Override
            public HistoryHandler getHistoryHandler() {
                if (historyHandler == null) {
                    throw new IllegalStateException("HistoryHandler not initialized");
                }
                return historyHandler;
            }

            @Override
            public SessionLifecycleManager getSessionLifecycleManager() {
                return sessionLifecycleManager;
            }

            @Override
            public StreamMessageCoalescer getStreamCoalescer() {
                return streamCoalescer;
            }

            @Override
            public WebviewWatchdog getWebviewWatchdog() {
                return webviewWatchdog;
            }

            @Override
            public boolean isDisposed() {
                return disposed;
            }

            @Override
            public boolean isInitialized() {
                return initialized;
            }

            @Override
            public boolean isFrontendReady() {
                return frontendReady;
            }

            @Override
            public void callJavaScript(String functionName, String... args) {
                ProteanChatWindow.this.callJavaScript(functionName, args);
            }

            @Override
            public void executeJavaScriptCode(String jsCode) {
                ProteanChatWindow.this.executeJavaScriptCode(jsCode);
            }

            @Override
            public int getTabIndex() {
                return ProteanChatWindow.this.getTabIndex();
            }

            @Override
            public void persistTabSessionState() {
                ProteanChatWindow.this.persistTabSessionState();
            }

            @Override
            public void addCodeSnippetFromExternal(String selectionInfo) {
                ProteanChatWindow.this.addCodeSnippetFromExternal(selectionInfo);
            }

            @Override
            public void updateTabLoadingState(boolean loading) {
                // 已废弃的桩
            }

            @Override
            public PendingCodeSnippetBuffer getPendingCodeSnippetBuffer() {
                return pendingCodeSnippetBuffer;
            }

            @Override
            public void setFrontendReady(boolean ready) {
                frontendReady = ready;
                if (ready) {
                    flushPendingCodeSnippet();
                }
            }

            @Override
            public String getSessionId() {
                return sessionId;
            }
        };
    }

    // ===== 公共 API =====

    public void setParentContent(Content content) {
        parentContent = content;
    }

    public void setOriginalTabName(String name) {
        if (name != null) {
            originalTabName = name.replaceAll("\\.{2,}$", "");
        } else {
            originalTabName = null;
        }
    }

    public boolean isDisposed() {
        return disposed;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public Content getParentContent() {
        return parentContent;
    }

    public JPanel getContent() {
        return mainPanel;
    }

    public SdkBridge getSdkBridge() {
        return sdkBridge;
    }

    public Project getProject() {
        return project;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getCurrentProvider() {
        return session.provider;
    }

    public ChatSession getSession() {
        return session;
    }

    public SessionLifecycleManager getSessionLifecycleManager() {
        return sessionLifecycleManager;
    }

    public void restorePersistedTabSessionState(TabStateService.TabSessionState state) {
        restorePersistedTabSessionState(state, false);
    }

    public void restorePersistedTabSessionState(TabStateService.TabSessionState state, boolean loadImmediately) {
        if (state == null) return;
        if (state.sessionId() != null) {
            session.setSessionInfo(state.sessionId(), state.cwd());
            if (loadImmediately) {
                TabStateService.TabSessionState ss = state;
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!disposed) {
                        String sid = ss.sessionId();
                        if (sid == null) return;
                        String cwd = ss.cwd() != null ? ss.cwd() : (project.getBasePath() != null ? project.getBasePath() : "");
                        sessionLifecycleManager.loadHistorySession(sid, cwd);
                    }
                });
            }
        }
    }

    public void loadRestoredHistoryIfNeeded() {
        TabStateService.TabSessionState state = TabStateService.getInstance(project).getTabSessionState(getTabIndex());
        if (state != null && state.sessionId() != null && restoredHistoryLoadStarted.compareAndSet(false, true)) {
            loadRestoredHistoryIfNeeded(state);
        }
    }

    private void loadRestoredHistoryIfNeeded(TabStateService.TabSessionState state) {
        if (state.sessionId() == null) return;
        String sid = state.sessionId();
        if (sid == null) return;
        String cwd = state.cwd() != null ? state.cwd() : (project.getBasePath() != null ? project.getBasePath() : "");
        ApplicationManager.getApplication().invokeLater(() -> {
            if (!disposed) {
                sessionLifecycleManager.loadHistorySession(sid, cwd);
            }
        });
    }

    public void addCodeSnippetFromExternal(String selectionInfo) {
        if (selectionInfo == null || selectionInfo.isEmpty()) return;
        String emitted = pendingCodeSnippetBuffer.offer(selectionInfo, frontendReady);
        if (emitted != null) {
            addCodeSnippet(emitted);
        }
    }

    public void updateTabStatus(ChatWindowDelegate.TabAnswerStatus status) {
        chatWindowDelegate.updateTabStatus(status);
    }

    public void sendQuickFixMessage(String prompt, boolean isQuickFix) {
        chatWindowDelegate.sendQuickFixMessage(prompt, isQuickFix);
    }

    public void executeJavaScriptCode(String jsCode) {
        if (handlerContext != null) {
            handlerContext.executeJavaScriptOnEDT(jsCode);
        }
    }

    public void focusInputPane() {
        callJavaScript("focusChatInput");
    }

    // ===== 内部 JavaScript 桥接 =====

    public void callJavaScript(String functionName, String... args) {
        if (disposed) return;

        if (!JsUtils.SAFE_JS_NAME.matcher(functionName).matches()) {
            LOG.warn("Unsafe JavaScript function name rejected: " + functionName);
            return;
        }

        ApplicationManager.getApplication().invokeLater(() -> {
            if (disposed) return;
            JBCefBrowser b = browser;
            if (b == null) return;

            try {
                StringBuilder argsStr = new StringBuilder();
                for (int i = 0; i < args.length; i++) {
                    if (i > 0) {
                        argsStr.append(",");
                    }
                    argsStr.append("\"").append(JsUtils.escapeJs(args[i])).append("\"");
                }
                String fn = functionName.contains(".") ? functionName : "window." + functionName;
                String call = "try { " + fn + "(" + argsStr.toString() + ") } catch(e) { console.error('"
                    + functionName + " call failed:', e); }";

                b.getCefBrowser().executeJavaScript(call, b.getCefBrowser().getURL(), 0);
            } catch (Exception e) {
                LOG.warn("Failed to call JavaScript '" + functionName + "': " + e.getMessage());
            }
        });
    }

    public void handleJavaScriptMessage(String message) {
        if (disposed) return;

        try {
            String trimmed = message.trim();

            if (trimmed.startsWith("{")) {
                Pattern typePattern = Pattern.compile("\"type\"\\s*:\\s*\"([^\"]+)\"");
                Pattern contentPattern = Pattern.compile("\"content\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
                Pattern rawContentPattern = Pattern.compile("\"content\"\\s*:\\s*(\\{.*?}|\\[.*?\\])");

                Matcher typeMatcher = typePattern.matcher(trimmed);
                String type = typeMatcher.find() ? typeMatcher.group(1) : "";

                Matcher rawMatcher = rawContentPattern.matcher(trimmed);
                String rawContent = rawMatcher.find() ? rawMatcher.group(1) : null;

                String content;
                if (rawContent != null) {
                    content = rawContent;
                } else {
                    Matcher contentMatcher = contentPattern.matcher(trimmed);
                    content = contentMatcher.find() ? contentMatcher.group(1) : trimmed;
                }

                switch (type) {
                    case "__frontend_ready__" -> {
                        frontendReady = true;
                        flushPendingCodeSnippet();
                        return;
                    }
                    case "heartbeat" -> {
                        webviewWatchdog.handleHeartbeat(content);
                        return;
                    }
                    case "console" -> {
                        LOG.info("[Webview] " + content);
                        return;
                    }
                }

                if (messageDispatcher != null) {
                    messageDispatcher.dispatch(type, content);
                }
            } else if (trimmed.contains(":")) {
                int colonIndex = trimmed.indexOf(":");
                String key = trimmed.substring(0, colonIndex).trim();
                String value = trimmed.substring(colonIndex + 1).trim();

                switch (key) {
                    case "heartbeat" -> webviewWatchdog.handleHeartbeat(value);
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to handle JavaScript message: " + e.getMessage());
        }
    }

    // ===== 私有辅助方法 =====

    private void setupSessionCallbacks() {
        if (sessionCallbackAdapter == null) {
            sessionCallbackAdapter = new SessionCallbackAdapter(
                streamCoalescer,
                new SessionCallbackAdapter.JsTarget() {
                    @Override
                    public void callJavaScript(String functionName, String... args) {
                        ProteanChatWindow.this.callJavaScript(functionName, args);
                    }
                },
                permissionHandler,
                () -> slashCommandsFetched,
                () -> onStreamEnded()
            );
        }
        session.setCallback(sessionCallbackAdapter);
    }

    private void onStreamEnded() {
        LOG.info("Stream ended for session: " + sessionId);
        streamCoalescer.resetStreamState();
    }

    private void initializeSessionInfo() {
        String basePath = project.getBasePath();
        if (basePath != null) {
            session.setSessionInfo(null, basePath);
        }
    }

    private void registerSessionLoadListener() {
        SessionLoadService.setListener((sid, path) -> {
            if (!disposed) {
                ApplicationManager.getApplication().invokeLater(() -> {
                    if (!disposed && restoredHistoryLoadStarted.compareAndSet(false, true)) {
                        sessionLifecycleManager.loadHistorySession(sid, path);
                    }
                });
            }
        });
    }

    private void registerInstance() {
        LOG.info("Window instance registered for project: " + project.getName());
    }

    private int getTabIndex() {
        Content content = parentContent;
        if (content == null) return -1;
        try {
            var manager = content.getManager();
            if (manager == null) return -1;
            for (int i = 0; i < manager.getContentCount(); i++) {
                if (manager.getContent(i) == content) {
                    return i;
                }
            }
            return -1;
        } catch (Exception e) {
            return -1;
        }
    }

    private void persistTabSessionState() {
        int tabIndex = getTabIndex();
        if (tabIndex < 0) return;

        TabStateService.TabSessionState state = new TabStateService.TabSessionState(
            session.provider,
            sessionId,
            session.cwd,
            session.model,
            session.permissionMode,
            session.reasoningEffort
        );
        TabStateService.getInstance(project).saveTabSessionState(tabIndex, state);
    }

    private void addCodeSnippet(String selectionInfo) {
        callJavaScript("addCodeSnippet", JsUtils.escapeJs(selectionInfo));
    }

    private void flushPendingCodeSnippet() {
        String snippet = pendingCodeSnippetBuffer.takePending();
        if (snippet != null) {
            addCodeSnippet(snippet);
        }
    }

    // ===== 释放 =====

    public synchronized void dispose() {
        if (disposed) return;
        disposed = true;

        LOG.info("Disposing ProteanChatWindow for project: " + project.getName());

        try {
            chatWindowDelegate.dispose();
        } catch (Exception e) {
            LOG.warn("Failed to dispose chatWindowDelegate: " + e.getMessage());
        }
        try {
            editorContextTracker.dispose();
        } catch (Exception e) {
            LOG.warn("Failed to dispose editorContextTracker: " + e.getMessage());
        }
        try {
            streamCoalescer.dispose();
        } catch (Exception e) {
            LOG.warn("Failed to dispose streamCoalescer: " + e.getMessage());
        }

        try {
            if (sessionCallbackAdapter != null) {
                sessionCallbackAdapter.dispose();
            }
            sessionCallbackAdapter = null;
        } catch (Exception e) {
            LOG.warn("Failed to dispose sessionCallbackAdapter: " + e.getMessage());
        }

        try {
            if (titleEventListener != null) {
                sdkBridge.removeDaemonEventListener(titleEventListener);
            }
        } catch (Exception e) {
            LOG.warn("Failed to remove daemon event listener: " + e.getMessage());
        }
        titleEventListener = null;

        try {
            webviewWatchdog.stop();
        } catch (Exception e) {
            LOG.warn("Failed to stop webview watchdog: " + e.getMessage());
        }

        try {
            PermissionService.removeInstance(sessionId != null ? sessionId : "");
        } catch (Exception e) {
            LOG.warn("Failed to remove PermissionService instance: " + e.getMessage());
        }

        LOG.info("Starting window resource cleanup, project: " + project.getName());

        try {
            if (handlerContext != null) {
                handlerContext.setDisposed(true);
            }
        } catch (Exception e) {
            LOG.warn("Failed to mark handlerContext disposed: " + e.getMessage());
        }

        try {
            session.interrupt();
        } catch (Exception e) {
            LOG.warn("Failed to clean up session: " + e.getMessage());
        }

        int activeProcessCount = sdkBridge.getActiveProcessCount();
        if (activeProcessCount > 0) {
            LOG.info("Cleaning up " + activeProcessCount + " active SDK processes");
        }
        try {
            sdkBridge.cleanupAllProcesses();
        } catch (Exception e) {
            LOG.warn("Failed to clean up SDK processes: " + e.getMessage());
        }

        try {
            if (browser != null) {
                browser.dispose();
            }
            browser = null;
        } catch (Exception e) {
            LOG.warn("Failed to clean up browser: " + e.getMessage());
        }

        try {
            if (messageDispatcher != null) {
                messageDispatcher.clear();
            }
        } catch (Exception e) {
            LOG.warn("Failed to clear message dispatcher: " + e.getMessage());
        }

        LOG.info("Window resources fully cleaned up, project: " + project.getName());
    }
}
