package com.protean.copilot.session;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Parses provider history messages into ChatSession messages.
 */
public class MessageParser {

    public ChatSession.Message parseServerMessage(JsonObject msg) {
        String type = msg.has("type") ? msg.get("type").getAsString() : null;
        JsonObject rawMessage = resolveRawMessage(msg);

        if (msg.has("isMeta") && msg.get("isMeta").getAsBoolean()) {
            return null;
        }
        if (shouldFilterCommandMessage(rawMessage, type)) {
            return null;
        }

        if ("user".equals(type)) {
            String content = extractMessageContent(msg);
            if (content == null || content.trim().isEmpty()) {
                if (hasToolResult(rawMessage)) {
                    return new ChatSession.Message(ChatSession.Message.Type.USER, "[tool_result]", rawMessage);
                }
                if (hasImageContent(rawMessage)) {
                    return new ChatSession.Message(ChatSession.Message.Type.USER, "", rawMessage);
                }
                return null;
            }
            return new ChatSession.Message(ChatSession.Message.Type.USER, content, rawMessage);
        }
        if ("assistant".equals(type)) {
            String content = extractMessageContent(msg);
            return new ChatSession.Message(ChatSession.Message.Type.ASSISTANT, content, rawMessage);
        }
        return null;
    }

    public boolean hasToolResult(JsonObject msg) {
        return hasContentBlockType(msg, "tool_result");
    }

    public boolean hasImageContent(JsonObject msg) {
        return hasContentBlockType(msg, "image");
    }

    public String extractMessageContent(JsonObject msg) {
        if (!msg.has("message")) {
            if (msg.has("content")) {
                return extractContentFromElement(msg.get("content"));
            }
            return "";
        }
        JsonObject message = msg.getAsJsonObject("message");
        if (!message.has("content") || message.get("content").isJsonNull()) {
            return "";
        }
        return extractContentFromElement(message.get("content"));
    }

    private JsonObject resolveRawMessage(JsonObject msg) {
        if (msg.has("raw") && msg.get("raw").isJsonObject()) {
            return msg.getAsJsonObject("raw");
        }
        return msg;
    }

    private boolean shouldFilterCommandMessage(JsonObject msg, String type) {
        if (!"user".equals(type) || !msg.has("message") || !msg.get("message").isJsonObject()) {
            return false;
        }
        JsonObject message = msg.getAsJsonObject("message");
        if (!message.has("content")) {
            return false;
        }

        JsonElement contentElement = message.get("content");
        String contentStr = null;
        if (contentElement.isJsonPrimitive()) {
            contentStr = contentElement.getAsString();
        } else if (contentElement.isJsonArray()) {
            JsonArray contentArray = contentElement.getAsJsonArray();
            for (int i = 0; i < contentArray.size(); i++) {
                JsonElement element = contentArray.get(i);
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject block = element.getAsJsonObject();
                if (block.has("type") && "text".equals(block.get("type").getAsString()) && block.has("text")) {
                    contentStr = block.get("text").getAsString();
                    break;
                }
            }
        }

        if (contentStr != null) {
            boolean hasCommandMessage = contentStr.contains("<command-message>")
                && contentStr.contains("</command-message>");
            if (!hasCommandMessage && (
                contentStr.contains("<command-name>")
                    || contentStr.contains("<local-command-stdout>")
                    || contentStr.contains("<local-command-stderr>")
                    || contentStr.contains("<command-args>")
            )) {
                return true;
            }
        }
        return false;
    }

    private boolean hasContentBlockType(JsonObject msg, String blockType) {
        if (msg.has("content") && containsContentBlockType(msg.get("content"), blockType)) {
            return true;
        }
        if (msg.has("message") && msg.get("message").isJsonObject()) {
            JsonObject message = msg.getAsJsonObject("message");
            if (message.has("content") && containsContentBlockType(message.get("content"), blockType)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsContentBlockType(JsonElement contentElement, String blockType) {
        if (contentElement == null || !contentElement.isJsonArray()) {
            return false;
        }
        JsonArray contentArray = contentElement.getAsJsonArray();
        for (int i = 0; i < contentArray.size(); i++) {
            JsonElement element = contentArray.get(i);
            if (element.isJsonObject()) {
                JsonObject block = element.getAsJsonObject();
                if (block.has("type") && blockType.equals(block.get("type").getAsString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String extractContentFromElement(JsonElement contentElement) {
        if (contentElement.isJsonPrimitive()) {
            return contentElement.getAsString();
        }
        if (contentElement.isJsonArray()) {
            return extractFromArrayContent(contentElement.getAsJsonArray());
        }
        if (contentElement.isJsonObject()) {
            JsonObject contentObj = contentElement.getAsJsonObject();
            if (contentObj.has("text") && !contentObj.get("text").isJsonNull()) {
                return contentObj.get("text").getAsString();
            }
        }
        return "";
    }

    private String extractFromArrayContent(JsonArray contentArray) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < contentArray.size(); i++) {
            JsonElement element = contentArray.get(i);
            if (element.isJsonObject()) {
                JsonObject block = element.getAsJsonObject();
                String blockType = block.has("type") && !block.get("type").isJsonNull()
                    ? block.get("type").getAsString()
                    : null;
                if ("text".equals(blockType) && block.has("text") && !block.get("text").isJsonNull()) {
                    if (builder.length() > 0) {
                        builder.append("\n");
                    }
                    builder.append(block.get("text").getAsString());
                }
            } else if (element.isJsonPrimitive()) {
                String text = element.getAsString();
                if (text != null && !text.trim().isEmpty()) {
                    if (builder.length() > 0) {
                        builder.append("\n");
                    }
                    builder.append(text);
                }
            }
        }
        return builder.toString();
    }
}
