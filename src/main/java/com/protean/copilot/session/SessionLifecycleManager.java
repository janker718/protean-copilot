package com.protean.copilot.session;

import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.history.HistoryMetadataService;
import com.protean.copilot.provider.claude.ClaudeHistoryReader;
import com.protean.copilot.settings.manager.WorkingDirectoryManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.jcef.JBCefBrowser;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 管理会话生命周期操作：创建、历史加载和工作目录解析。
 *
 * 从参考实现移植而来。最初使用存根，后续可连接到真实的 SDK 后端。
 */
public class SessionLifecycleManager {

    private static final Logger LOG = Logger.getInstance(SessionLifecycleManager.class);
    private static final Gson GSON = new Gson();

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
    public void loadHistorySession(String sessionId, String projectPath) {
        LOG.info("Loading history session: " + sessionId + " from project: " + projectPath);

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
            newSession.setProvider(previousProvider);
            newSession.setModel(previousModel);

            host.setSession(newSession);
            host.getHandlerContext().setSession(newSession);
            host.setupSessionCallbacks();

            String resolvedWorkingDirectory = WorkingDirectoryManager.getInstance(host.getProject())
                .resolveWorkingDirectory(projectPath);
            newSession.setSessionInfo(sessionId, resolvedWorkingDirectory);
            restoreClaudeHistoryMessages(newSession, resolvedWorkingDirectory, sessionId);
            HistoryMetadataService.getInstance(host.getProject()).updateFromSession(newSession);

            ApplicationManager.getApplication().invokeLater(() -> {
                host.callJavaScript("historyLoadComplete");
                if (!newSession.getMessages().isEmpty()) {
                    host.callJavaScript("updateMessages", buildFrontendMessagesJson(newSession.getMessages()));
                }
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
        host.setSession(newSession);
        host.getHandlerContext().setSession(newSession);
        host.setupSessionCallbacks();

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

    private void restoreClaudeHistoryMessages(ChatSession session, String projectPath, String sessionId) {
        ClaudeHistoryReader historyReader = new ClaudeHistoryReader();
        MessageParser parser = new MessageParser();
        List<ClaudeHistoryReader.ConversationMessage> rawMessages = historyReader.readSessionMessages(projectPath, sessionId);
        List<ChatSession.Message> parsedMessages = new ArrayList<>();

        for (ClaudeHistoryReader.ConversationMessage rawMessage : rawMessages) {
            JsonObject payload = GSON.toJsonTree(rawMessage).getAsJsonObject();
            ChatSession.Message parsed = parser.parseServerMessage(payload);
            if (parsed == null) {
                continue;
            }
            if (rawMessage.timestamp != null) {
                try {
                    parsed.timestamp = Instant.parse(rawMessage.timestamp).toEpochMilli();
                } catch (Exception ignored) {
                }
            }
            parsedMessages.add(parsed);
        }

        session.replaceMessages(parsedMessages);
        if (!parsedMessages.isEmpty() && (session.getSummary() == null || session.getSummary().isBlank())) {
            String summary = parsedMessages.get(0).content;
            if (summary != null && !summary.isBlank()) {
                session.setSummary(summary.length() > 45 ? summary.substring(0, 45) + "..." : summary);
            }
        }
    }

    private String buildFrontendMessagesJson(List<ChatSession.Message> messages) {
        JsonArray array = new JsonArray();
        for (ChatSession.Message message : messages) {
            JsonObject item = new JsonObject();
            item.addProperty("type", message.type.name().toLowerCase());
            item.addProperty("content", message.content == null ? "" : message.content);
            item.addProperty("timestamp", Instant.ofEpochMilli(message.timestamp).toString());
            if (message.raw != null) {
                item.add("raw", message.raw);
            }
            array.add(item);
        }
        return GSON.toJson(array);
    }
}
