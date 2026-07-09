package com.protean.copilot.session;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * 会话加载回调服务。
 * 管理一个单一的监听器，当需要从历史记录中加载会话时调用。
 */
public final class SessionLoadService {

    private static final Logger LOG = Logger.getInstance(SessionLoadService.class);

    private SessionLoadService() {
        // private constructor for singleton
    }

    private static volatile Consumer<HistorySessionLoadRequest> listener = null;
    private static volatile HistorySessionLoadRequest pendingRequest = null;

    /**
     * 设置将被调用以加载会话的监听器。
     */
    public static void setListener(@NotNull Consumer<HistorySessionLoadRequest> listener) {
        SessionLoadService.listener = listener;
        LOG.info("SessionLoadService listener " + (listener != null ? "registered" : "cleared"));
        HistorySessionLoadRequest request = pendingRequest;
        if (request != null) {
            pendingRequest = null;
            listener.accept(request);
        }
    }

    public static void clearListener() {
        listener = null;
        pendingRequest = null;
    }

    /**
     * 触发会话加载。由外部组件在需要恢复持久化会话时调用。
     */
    public static void triggerLoad(String sessionId, String projectPath) {
        triggerLoad(HistorySessionLoadRequest.of(sessionId, projectPath, null));
    }

    public static void triggerLoad(HistorySessionLoadRequest request) {
        if (request == null || request.sessionId() == null) {
            return;
        }
        Consumer<HistorySessionLoadRequest> currentListener = listener;
        if (currentListener != null) {
            LOG.info("Triggering session load: sessionId=" + request.sessionId()
                + ", projectPath=" + request.projectPath()
                + ", provider=" + request.provider());
            currentListener.accept(request);
        } else {
            pendingRequest = request;
            LOG.info("Queued pending session load: sessionId=" + request.sessionId()
                + ", projectPath=" + request.projectPath()
                + ", provider=" + request.provider());
        }
    }
}
