package com.protean.copilot.session;

import com.protean.copilot.handler.HandlerContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.jcef.JBCefBrowser;

/**
 * 管理会话生命周期操作：创建、历史加载和工作目录解析。
 *
 * 从参考实现移植而来。最初使用存根，后续可连接到真实的 SDK 后端。
 */
public class SessionLifecycleManager {

    private static final Logger LOG = Logger.getInstance(SessionLifecycleManager.class);

    /**
     * 宿主接口，提供对窗口级依赖的访问。
     */
    public interface SessionHost {
        Project getProject();
        ChatSession getSession();
        void setSession(ChatSession session);
        HandlerContext getHandlerContext();
        StreamMessageCoalescer getStreamCoalescer();
        void clearPendingPermissionRequests();
        void clearPermissionDecisionMemory();
        void callJavaScript(String functionName, String... args);
        boolean isDisposed();
        JBCefBrowser getBrowser();
        void setupSessionCallbacks();
        void invalidateSessionCallbacks();
        void setSlashCommandsFetched(boolean fetched);
        void setFetchedSlashCommandsCount(int count);
    }

    private final SessionHost host;

    public SessionLifecycleManager(SessionHost host) {
        this.host = host;
    }

    /**
     * 创建新会话，首先中断旧会话。
     */
    public void createNewSession() {
        LOG.info("Creating new session...");

        ChatSession oldSession = host.getSession();
        String previousMode = oldSession.permissionMode;
        String previousProvider = oldSession.provider;
        String previousModel = oldSession.model;

        host.invalidateSessionCallbacks();
        host.getStreamCoalescer().resetStreamState();
        host.callJavaScript("clearMessages");

        var interruptFuture = oldSession.interrupt();

        interruptFuture.thenRun(() -> {
            LOG.info("Old session interrupted, creating new session");

            ApplicationManager.getApplication().invokeLater(() -> {
                host.callJavaScript("onStreamEnd");
                host.callJavaScript("showLoading", "false");
            });

            ChatSession newSession = new ChatSession(host.getProject(), oldSession.sdkBridge);
            newSession.permissionMode = previousMode;
            newSession.provider = previousProvider;
            newSession.model = previousModel;

            completeNewSessionBootstrap(newSession);
        }).exceptionally(ex -> {
            LOG.error("Failed to create new session: " + ex.getMessage(), ex);
            ApplicationManager.getApplication().invokeLater(() -> {
                host.callJavaScript("historyLoadComplete");
                host.callJavaScript("updateStatus", "Failed to create new session: " + ex.getMessage());
            });
            return null;
        });
    }

    /**
     * 确定会话的工作目录。
     * 默认为项目根路径。
     */
    public String determineWorkingDirectory() {
        String projectPath = host.getProject().getBasePath();
        if (projectPath != null && new java.io.File(projectPath).exists()) {
            return projectPath;
        }
        String userHome = System.getProperty("user.home");
        return userHome != null ? userHome : ".";
    }

    /**
     * 按 ID 加载历史会话。
     */
    public void loadHistorySession(String sessionId, String projectPath) {
        LOG.info("Loading history session: " + sessionId + " from project: " + projectPath);
        // 存根：历史加载尚未实现
        ApplicationManager.getApplication().invokeLater(() -> {
            host.callJavaScript("historyLoadComplete");
        });
    }

    private void completeNewSessionBootstrap(ChatSession newSession) {
        host.clearPendingPermissionRequests();
        host.clearPermissionDecisionMemory();
        host.setSession(newSession);
        host.getHandlerContext().setSession(newSession);
        host.setupSessionCallbacks();

        String workingDir = determineWorkingDirectory();
        newSession.setSessionInfo(null, workingDir);
        LOG.info("New session created successfully, working directory: " + workingDir);

        ApplicationManager.getApplication().invokeLater(() -> {
            host.callJavaScript("historyLoadComplete");
            host.callJavaScript("updateStatus", "New session created successfully");
        });
    }
}
