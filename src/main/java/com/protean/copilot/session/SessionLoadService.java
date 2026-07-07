package com.protean.copilot.session;

import com.intellij.openapi.diagnostic.Logger;
import java.util.function.BiConsumer;

/**
 * 会话加载回调服务。
 * 管理一个单一的监听器，当需要从历史记录中加载会话时调用。
 */
public final class SessionLoadService {

    private static final Logger LOG = Logger.getInstance(SessionLoadService.class);

    private SessionLoadService() {
        // private constructor for singleton
    }

    private static volatile BiConsumer<String, String> listener = null;

    /**
     * 设置将被调用以加载会话的监听器。
     */
    public static void setListener(BiConsumer<String, String> listener) {
        SessionLoadService.listener = listener;
        LOG.info("SessionLoadService listener " + (listener != null ? "registered" : "cleared"));
    }

    /**
     * 触发会话加载。由外部组件在需要恢复持久化会话时调用。
     */
    public static void triggerLoad(String sessionId, String projectPath) {
        BiConsumer<String, String> currentListener = listener;
        if (currentListener != null) {
            LOG.info("Triggering session load: sessionId=" + sessionId + ", projectPath=" + projectPath);
            currentListener.accept(sessionId, projectPath);
        } else {
            LOG.warn("Session load triggered but no listener registered");
        }
    }
}
