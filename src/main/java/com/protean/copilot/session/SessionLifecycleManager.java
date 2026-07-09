package com.protean.copilot.session;

import com.protean.copilot.history.HistoryMetadataService;
import com.protean.copilot.settings.manager.WorkingDirectoryManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import java.util.concurrent.CompletableFuture;

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
        void activateSession(ChatSession session);
        StreamMessageCoalescer getStreamCoalescer();
        void clearPendingPermissionRequests();
        void clearPermissionDecisionMemory();
        void callJavaScript(String functionName, String... args);
        void invalidateSessionCallbacks();
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
        ChatSession defaultSession = createDefaultSession();
        String previousMode = oldSession != null ? oldSession.getPermissionMode() : defaultSession.getPermissionMode();
        String previousProvider = oldSession != null ? oldSession.getProvider() : defaultSession.getProvider();
        String previousModel = oldSession != null ? oldSession.getModel() : defaultSession.getModel();

        host.invalidateSessionCallbacks();
        host.getStreamCoalescer().resetStreamState();
        host.callJavaScript("clearMessages");

        CompletableFuture<Void> interruptFuture = oldSession != null
            ? oldSession.interrupt()
            : CompletableFuture.completedFuture(null);

        interruptFuture.thenRun(() -> {
            LOG.info("Old session interrupted, creating new session");

            ApplicationManager.getApplication().invokeLater(() -> {
                host.callJavaScript("onStreamEnd");
                host.callJavaScript("showLoading", "false");
            });

            ChatSession newSession = createDefaultSession();
            newSession.setPermissionMode(previousMode);
            newSession.setProvider(previousProvider);
            newSession.setModel(previousModel);

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
     * 统一通过 WorkingDirectoryManager 解析。
     */
    public String determineWorkingDirectory() {
        return WorkingDirectoryManager.getInstance(host.getProject()).resolveWorkingDirectory();
    }

    /**
     * 按 ID 加载历史会话。
     */
    public void loadHistorySession(String sessionId, String projectPath, String provider) {
        loadHistorySession(HistorySessionLoadRequest.of(sessionId, projectPath, provider));
    }

    public void loadHistorySession(HistorySessionLoadRequest request) {
        if (request == null || request.sessionId() == null) {
            return;
        }
        LOG.info("Loading history session: " + request.sessionId()
            + " from project: " + request.projectPath()
            + ", provider: " + request.provider());
        ChatSession oldSession = host.getSession();
        ChatSession defaultSession = createDefaultSession();
        String previousMode = oldSession != null ? oldSession.getPermissionMode() : defaultSession.getPermissionMode();
        String previousProvider = oldSession != null ? oldSession.getProvider() : defaultSession.getProvider();
        String previousModel = oldSession != null ? oldSession.getModel() : defaultSession.getModel();

        host.invalidateSessionCallbacks();
        host.getStreamCoalescer().resetStreamState();
        host.callJavaScript("clearMessages");
        host.clearPendingPermissionRequests();
        host.clearPermissionDecisionMemory();

        CompletableFuture<Void> interruptFuture = oldSession != null
            ? oldSession.interrupt()
            : CompletableFuture.completedFuture(null);

        interruptFuture.thenRun(() -> {
            ChatSession newSession = createDefaultSession();
            newSession.setPermissionMode(previousMode);
            newSession.setProvider(request.provider() != null && !request.provider().isBlank() ? request.provider() : previousProvider);
            newSession.setModel(previousModel);

            host.activateSession(newSession);

            String resolvedWorkingDirectory = WorkingDirectoryManager.getInstance(host.getProject())
                .resolveWorkingDirectory(request.projectPath());
            newSession.setSessionInfo(request.sessionId(), resolvedWorkingDirectory);
            newSession.loadFromServer().whenComplete((v, ex) -> {
                if (ex == null) {
                    HistoryMetadataService.getInstance(host.getProject()).updateFromSession(newSession);
                }
                ApplicationManager.getApplication().invokeLater(() -> {
                    host.callJavaScript("historyLoadComplete");
                    if (ex != null) {
                        host.callJavaScript("addErrorMessage", "Failed to load history session: " + ex.getMessage());
                    }
                });
            });
        }).exceptionally(ex -> {
            LOG.error("Failed to load history session: " + ex.getMessage(), ex);
            ApplicationManager.getApplication().invokeLater(() -> {
                host.callJavaScript("historyLoadComplete");
                host.callJavaScript("addErrorMessage", "Failed to load history session: " + ex.getMessage());
            });
            return null;
        });
    }

    private void completeNewSessionBootstrap(ChatSession newSession) {
        host.clearPendingPermissionRequests();
        host.clearPermissionDecisionMemory();
        host.activateSession(newSession);

        String workingDir = determineWorkingDirectory();
        newSession.setSessionInfo(null, workingDir);
        HistoryMetadataService.getInstance(host.getProject()).updateFromSession(newSession);
        LOG.info("New session created successfully, working directory: " + workingDir);

        ApplicationManager.getApplication().invokeLater(() -> {
            host.callJavaScript("historyLoadComplete");
            host.callJavaScript("updateStatus", "New session created successfully");
        });
    }

    private ChatSession createDefaultSession() {
        return new ChatSession(host.getProject(), host.getSession().getSdkBridge());
    }
}
