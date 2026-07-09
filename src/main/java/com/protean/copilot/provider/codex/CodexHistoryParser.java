package com.protean.copilot.provider.codex;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class CodexHistoryParser {

    private static final Logger LOG = Logger.getInstance(CodexHistoryParser.class);

    private final Gson gson;

    CodexHistoryParser(@NotNull Gson gson) {
        this.gson = gson;
    }

    CodexHistoryReader.SessionInfo parseSessionFile(@NotNull Path sessionFile) throws IOException {
        CodexHistoryReader.SessionInfo session = new CodexHistoryReader.SessionInfo();
        session.sessionId = stripJsonlExtension(sessionFile.getFileName().toString());
        session.fileSize = safeFileSize(sessionFile);
        session.entrypoint = "codex-cli";

        try (BufferedReader reader = Files.newBufferedReader(sessionFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                JsonObject root = parseLine(line);
                if (root == null) {
                    continue;
                }

                long timestamp = extractTimestamp(root);
                if (timestamp > 0) {
                    if (session.firstTimestamp <= 0 || timestamp < session.firstTimestamp) {
                        session.firstTimestamp = timestamp;
                    }
                    if (timestamp > session.lastTimestamp) {
                        session.lastTimestamp = timestamp;
                    }
                }

                String rootType = getAsString(root, "type");
                JsonObject payload = root.has("payload") && root.get("payload").isJsonObject()
                    ? root.getAsJsonObject("payload")
                    : null;

                if ("session_meta".equals(rootType) && payload != null) {
                    String metaId = firstNonBlank(getAsString(payload, "id"), getAsString(root, "sessionId"));
                    if (metaId != null) {
                        session.sessionId = metaId;
                    }
                    session.cwd = firstNonBlank(session.cwd, getAsString(payload, "cwd"));
                    long metaTimestamp = parseTimestamp(getAsString(payload, "timestamp"));
                    if (metaTimestamp > 0) {
                        if (session.firstTimestamp <= 0 || metaTimestamp < session.firstTimestamp) {
                            session.firstTimestamp = metaTimestamp;
                        }
                        if (metaTimestamp > session.lastTimestamp) {
                            session.lastTimestamp = metaTimestamp;
                        }
                    }
                    continue;
                }

                if (payload == null) {
                    continue;
                }

                if (session.title == null && "event_msg".equals(rootType)) {
                    session.title = extractUserMessageTitle(payload);
                }

                JsonObject displayMessage = "event_msg".equals(rootType)
                    ? fromEventMessage(payload)
                    : toDisplayMessage(payload);
                if (displayMessage != null) {
                    session.messageCount++;
                }
            }
        }

        if (session.title == null || session.title.isBlank()) {
            session.title = "Untitled session";
        }
        return session;
    }

    @NotNull List<JsonObject> parseSessionMessages(@NotNull Path sessionFile) throws IOException {
        List<JsonObject> messages = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(sessionFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                JsonObject root = parseLine(line);
                if (root == null) {
                    continue;
                }

                String rootType = getAsString(root, "type");
                JsonObject payload = root.has("payload") && root.get("payload").isJsonObject()
                    ? root.getAsJsonObject("payload")
                    : null;
                if (payload == null) {
                    continue;
                }

                JsonObject displayMessage = null;
                if ("event_msg".equals(rootType)) {
                    displayMessage = fromEventMessage(payload);
                } else if ("response_item".equals(rootType)) {
                    displayMessage = toDisplayMessage(payload);
                }

                if (displayMessage != null) {
                    messages.add(displayMessage);
                }
            }
        }
        return messages;
    }

    @Nullable String extractUserMessageTitle(@NotNull JsonObject payload) {
        if (!"user_message".equals(getAsString(payload, "type"))) {
            return null;
        }
        String visible = stripSystemTags(getAsString(payload, "message"));
        if (visible == null || visible.isBlank()) {
            return null;
        }
        String singleLine = visible.replace('\n', ' ').trim();
        return singleLine.length() > 45 ? singleLine.substring(0, 45) + "..." : singleLine;
    }

    @Nullable JsonObject fromEventMessage(@NotNull JsonObject payload) {
        if (!"user_message".equals(getAsString(payload, "type"))) {
            return null;
        }
        String visible = stripSystemTags(getAsString(payload, "message"));
        if (visible == null || visible.isBlank()) {
            return null;
        }

        JsonObject raw = new JsonObject();
        raw.addProperty("type", "user");
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        JsonArray content = new JsonArray();
        JsonObject textBlock = new JsonObject();
        textBlock.addProperty("type", "text");
        textBlock.addProperty("text", visible);
        content.add(textBlock);
        message.add("content", content);
        raw.add("message", message);
        return raw;
    }

    @Nullable JsonObject toDisplayMessage(@NotNull JsonObject payload) {
        String payloadType = getAsString(payload, "type");
        if (payloadType == null) {
            return null;
        }
        return switch (payloadType) {
            case "agent_message" -> buildAssistantTextMessage(firstNonBlank(
                getAsString(payload, "text"),
                extractTextBlocks(payload.get("content"))
            ));
            case "function_call" -> buildToolUseMessage(payload);
            case "function_call_output" -> buildToolResultMessage(payload);
            default -> null;
        };
    }

    static @NotNull String stripSystemTags(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String result = text;
        for (String tag : List.of("agents-instructions", "system-reminder", "system-prompt")) {
            result = removeTagBlock(result, tag);
        }
        return result.trim();
    }

    private static @NotNull String removeTagBlock(@NotNull String text, @NotNull String tagName) {
        String result = text;
        String openTag = "<" + tagName + ">";
        String closeTag = "</" + tagName + ">";
        while (true) {
            int start = result.indexOf(openTag);
            if (start < 0) {
                return result;
            }
            int end = result.indexOf(closeTag, start);
            if (end < 0) {
                return result;
            }
            result = result.substring(0, start) + result.substring(end + closeTag.length());
        }
    }

    private @Nullable JsonObject buildAssistantTextMessage(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        JsonObject raw = new JsonObject();
        raw.addProperty("type", "assistant");
        JsonObject message = new JsonObject();
        message.addProperty("role", "assistant");
        JsonArray content = new JsonArray();
        JsonObject textBlock = new JsonObject();
        textBlock.addProperty("type", "text");
        textBlock.addProperty("text", text);
        content.add(textBlock);
        message.add("content", content);
        raw.add("message", message);
        return raw;
    }

    private @Nullable JsonObject buildToolUseMessage(@NotNull JsonObject payload) {
        String toolName = firstNonBlank(getAsString(payload, "name"), getAsString(payload, "tool_name"));
        if (toolName == null) {
            return null;
        }

        JsonObject raw = new JsonObject();
        raw.addProperty("type", "assistant");
        JsonObject message = new JsonObject();
        message.addProperty("role", "assistant");
        JsonArray content = new JsonArray();
        JsonObject toolUse = new JsonObject();
        toolUse.addProperty("type", "tool_use");
        toolUse.addProperty("id", firstNonBlank(getAsString(payload, "call_id"), getAsString(payload, "id"), toolName));
        toolUse.addProperty("name", toolName);
        if (payload.has("arguments")) {
            toolUse.add("input", payload.get("arguments"));
        } else if (payload.has("input")) {
            toolUse.add("input", payload.get("input"));
        } else {
            toolUse.add("input", new JsonObject());
        }
        content.add(toolUse);
        message.add("content", content);
        raw.add("message", message);
        return raw;
    }

    private @Nullable JsonObject buildToolResultMessage(@NotNull JsonObject payload) {
        String toolUseId = firstNonBlank(getAsString(payload, "call_id"), getAsString(payload, "id"));
        if (toolUseId == null) {
            return null;
        }
        String output = firstNonBlank(getAsString(payload, "output"), extractTextBlocks(payload.get("content")), "(no output)");
        JsonObject raw = new JsonObject();
        raw.addProperty("type", "user");
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        JsonArray content = new JsonArray();
        JsonObject toolResult = new JsonObject();
        toolResult.addProperty("type", "tool_result");
        toolResult.addProperty("tool_use_id", toolUseId);
        toolResult.addProperty("is_error", "error".equalsIgnoreCase(getAsString(payload, "status")));
        toolResult.addProperty("content", output);
        content.add(toolResult);
        message.add("content", content);
        raw.add("message", message);
        return raw;
    }

    private @Nullable JsonObject parseLine(@Nullable String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        try {
            JsonElement element = gson.fromJson(line, JsonElement.class);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception ex) {
            LOG.debug("Failed to parse Codex history line: " + ex.getMessage());
            return null;
        }
    }

    private long extractTimestamp(@NotNull JsonObject root) {
        long parsed = parseTimestamp(getAsString(root, "timestamp"));
        if (parsed > 0) {
            return parsed;
        }
        JsonObject payload = root.has("payload") && root.get("payload").isJsonObject()
            ? root.getAsJsonObject("payload")
            : null;
        return payload != null ? parseTimestamp(getAsString(payload, "timestamp")) : 0L;
    }

    private long parseTimestamp(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private @Nullable String extractTextBlocks(@Nullable JsonElement contentElement) {
        if (contentElement == null || contentElement.isJsonNull()) {
            return null;
        }
        if (contentElement.isJsonPrimitive()) {
            return contentElement.getAsString();
        }
        if (!contentElement.isJsonArray()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (JsonElement element : contentElement.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject block = element.getAsJsonObject();
            String blockType = getAsString(block, "type");
            if (!List.of("text", "input_text", "output_text").contains(blockType)) {
                continue;
            }
            String text = getAsString(block, "text");
            if (text == null || text.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(text);
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    private static @Nullable String getAsString(@NotNull JsonObject object, @NotNull String member) {
        return object.has(member) && !object.get(member).isJsonNull() ? object.get(member).getAsString() : null;
    }

    private static @Nullable String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static @NotNull String stripJsonlExtension(@NotNull String fileName) {
        return fileName.endsWith(".jsonl") ? fileName.substring(0, fileName.length() - 6) : fileName;
    }

    private static long safeFileSize(@NotNull Path path) {
        try {
            return Files.size(path);
        } catch (IOException ignored) {
            return 0L;
        }
    }
}
