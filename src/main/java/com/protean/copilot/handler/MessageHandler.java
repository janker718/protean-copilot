package com.protean.copilot.handler;

import java.util.List;

/**
 * 聊天窗口中消息处理器的接口。
 * 每个处理器处理来自前端的特定消息类型。
 */
public interface MessageHandler {
    /**
     * 处理给定类型的消息。
     * @param type 消息类型标识符
     * @param content 消息内容（JSON 字符串或纯文本）
     * @return 如果处理器处理了该消息，则返回 true，否则返回 false
     */
    boolean handle(String type, String content);

    /**
     * 获取此处理器支持的消息类型列表。
     */
    List<String> getSupportedTypes();
}
