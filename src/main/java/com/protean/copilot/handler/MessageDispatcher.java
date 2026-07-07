package com.protean.copilot.handler;

import com.intellij.openapi.diagnostic.Logger;
import java.util.ArrayList;
import java.util.List;

/**
 * 将来自前端的消息路由到已注册的处理器。
 * 从参考实现移植而来。
 */
public class MessageDispatcher {

    private static final Logger LOG = Logger.getInstance(MessageDispatcher.class);

    private final List<MessageHandler> handlers = new ArrayList<>();

    /**
     * 注册一个消息处理器。
     */
    public void registerHandler(MessageHandler handler) {
        handlers.add(handler);
        LOG.info("Registered handler for types: " + handler.getSupportedTypes());
    }

    /**
     * 将消息分发给所有已注册的处理器。
     * @param type 消息类型
     * @param content 消息内容
     * @return 如果有任何处理器处理了该消息，则返回 true
     */
    public boolean dispatch(String type, String content) {
        boolean consumed = false;
        for (MessageHandler handler : handlers) {
            try {
                if (handler.handle(type, content)) {
                    consumed = true;
                }
            } catch (Exception e) {
                LOG.warn("Handler " + handler.getClass().getSimpleName() + " failed for type '" + type + "': " + e.getMessage());
            }
        }
        if (!consumed) {
            LOG.warn("No handler consumed message type: " + type);
        }
        return consumed;
    }

    /**
     * 检查是否有处理器支持给定的消息类型。
     */
    public boolean hasHandlerFor(String type) {
        return handlers.stream().anyMatch(h -> h.getSupportedTypes().contains(type));
    }

    /**
     * 获取已注册处理器的数量。
     */
    public int getHandlerCount() {
        return handlers.size();
    }

    /**
     * 清除所有已注册的处理器。
     */
    public void clear() {
        handlers.clear();
    }
}
