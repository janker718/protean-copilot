package com.protean.copilot.provider.common;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Lightweight session file reader that only reads head and tail chunks.
 */
public class SessionLiteReader {

    private static final Logger LOG = Logger.getInstance(SessionLiteReader.class);
    private static final Gson GSON = new Gson();

    public static final int LITE_READ_BUF_SIZE = 65536;

    public static class LiteSessionFile {
        public final long mtime;
        public final long size;
        public final String head;
        public final String tail;

        public LiteSessionFile(long mtime, long size, String head, String tail) {
            this.mtime = mtime;
            this.size = size;
            this.head = head;
            this.tail = tail;
        }
    }

    public LiteSessionFile readSessionLite(Path path) {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
            long fileSize = channel.size();
            long mtime = Files.getLastModifiedTime(path).toMillis();
            if (fileSize == 0) {
                return null;
            }

            ByteBuffer buffer = ByteBuffer.allocate(LITE_READ_BUF_SIZE);
            buffer.clear();
            int headBytesRead = channel.read(buffer, 0);
            if (headBytesRead <= 0) {
                return null;
            }
            String head = new String(buffer.array(), 0, headBytesRead, StandardCharsets.UTF_8);

            String tail = head;
            long tailOffset = Math.max(0, fileSize - LITE_READ_BUF_SIZE);
            if (tailOffset > 0) {
                buffer.clear();
                int tailBytesRead = channel.read(buffer, tailOffset);
                if (tailBytesRead > 0) {
                    tail = new String(buffer.array(), 0, tailBytesRead, StandardCharsets.UTF_8);
                }
            }

            return new LiteSessionFile(mtime, fileSize, head, tail);
        } catch (IOException e) {
            LOG.debug("[SessionLiteReader] Failed to read file: " + path + " - " + e.getMessage());
            return null;
        }
    }

    public String extractJsonStringField(String text, String key) {
        if (text == null || key == null) {
            return null;
        }

        String[] patterns = {"\"" + key + "\":\"", "\"" + key + "\": \""};
        for (String pattern : patterns) {
            int idx = text.indexOf(pattern);
            if (idx < 0) {
                continue;
            }

            int valueStart = idx + pattern.length();
            int i = valueStart;
            while (i < text.length()) {
                char c = text.charAt(i);
                if (c == '\\') {
                    i += 2;
                    continue;
                }
                if (c == '"') {
                    return unescapeJsonString(text.substring(valueStart, i));
                }
                i++;
            }
        }
        return null;
    }

    public String extractLastJsonStringField(String text, String key) {
        if (text == null || key == null) {
            return null;
        }

        String[] patterns = {"\"" + key + "\":\"", "\"" + key + "\": \""};
        String lastValue = null;
        for (String pattern : patterns) {
            int searchFrom = 0;
            while (true) {
                int idx = text.indexOf(pattern, searchFrom);
                if (idx < 0) {
                    break;
                }
                int valueStart = idx + pattern.length();
                int i = valueStart;
                while (i < text.length()) {
                    char c = text.charAt(i);
                    if (c == '\\') {
                        i += 2;
                        continue;
                    }
                    if (c == '"') {
                        lastValue = unescapeJsonString(text.substring(valueStart, i));
                        break;
                    }
                    i++;
                }
                searchFrom = i + 1;
            }
        }
        return lastValue;
    }

    public boolean isSidechainSession(String head) {
        if (head == null || head.isEmpty()) {
            return false;
        }
        int firstNewline = head.indexOf('\n');
        String firstLine = firstNewline >= 0 ? head.substring(0, firstNewline) : head;
        return firstLine.contains("\"isSidechain\":true") || firstLine.contains("\"isSidechain\": true");
    }

    public String extractFirstPromptFromHead(String head) {
        if (head == null || head.isEmpty()) {
            return null;
        }

        int start = 0;
        String commandFallback = null;
        while (start < head.length()) {
            int newlineIdx = head.indexOf('\n', start);
            String line = newlineIdx >= 0 ? head.substring(start, newlineIdx) : head.substring(start);
            start = newlineIdx >= 0 ? newlineIdx + 1 : head.length();

            if (!line.contains("\"type\":\"user\"") && !line.contains("\"type\": \"user\"")) {
                continue;
            }
            if (line.contains("\"tool_result\"")) {
                continue;
            }
            if (line.contains("\"isMeta\":true") || line.contains("\"isMeta\": true")) {
                continue;
            }
            if (line.contains("\"isCompactSummary\":true") || line.contains("\"isCompactSummary\": true")) {
                continue;
            }

            String contentText = extractContentFromLine(line);
            if (contentText == null || contentText.isEmpty()) {
                continue;
            }

            String result = contentText.replace("\n", " ").trim();
            if (result.startsWith("<command-name>") && result.contains("</command-name>")) {
                int nameStart = result.indexOf("<command-name>") + 14;
                int nameEnd = result.indexOf("</command-name>");
                if (nameEnd > nameStart) {
                    commandFallback = result.substring(nameStart, nameEnd);
                    continue;
                }
            }
            if (result.contains("<bash-input>")) {
                int bashStart = result.indexOf("<bash-input>") + 12;
                int bashEnd = result.indexOf("</bash-input>");
                if (bashEnd > bashStart) {
                    return "! " + result.substring(bashStart, bashEnd).trim();
                }
            }
            if (result.startsWith("<") && result.length() > 1 && Character.isLowerCase(result.charAt(1))) {
                continue;
            }
            if (result.startsWith("[Request interrupted")) {
                continue;
            }
            if (result.length() > 200) {
                result = result.substring(0, 200).trim() + "...";
            }
            return result;
        }
        return commandFallback;
    }

    public int countMessagesInHead(String head) {
        if (head == null || head.isEmpty()) {
            return 0;
        }
        int count = 0;
        int start = 0;
        while (start < head.length()) {
            int newlineIdx = head.indexOf('\n', start);
            String line = newlineIdx >= 0 ? head.substring(start, newlineIdx) : head.substring(start);
            start = newlineIdx >= 0 ? newlineIdx + 1 : head.length();
            if (line.trim().isEmpty()) {
                continue;
            }
            if (line.contains("\"isSidechain\":true") || line.contains("\"isSidechain\": true")) {
                continue;
            }
            count++;
        }
        return count;
    }

    private String extractContentFromLine(String line) {
        String content = extractJsonStringField(line, "content");
        if (content != null && !content.isEmpty()) {
            return content;
        }

        if (line.contains("\"content\":[") || line.contains("\"content\": [")) {
            try {
                JsonObject entry = GSON.fromJson(line, JsonObject.class);
                if (entry == null || !entry.has("message") || !entry.get("message").isJsonObject()) {
                    return null;
                }
                JsonObject message = entry.getAsJsonObject("message");
                if (!message.has("content")) {
                    return null;
                }
                JsonElement contentElem = message.get("content");
                if (contentElem.isJsonArray()) {
                    JsonArray contentArray = contentElem.getAsJsonArray();
                    StringBuilder builder = new StringBuilder();
                    for (JsonElement block : contentArray) {
                        if (!block.isJsonObject()) {
                            continue;
                        }
                        JsonObject blockObj = block.getAsJsonObject();
                        if (blockObj.has("type")
                            && "text".equals(blockObj.get("type").getAsString())
                            && blockObj.has("text")
                            && !blockObj.get("text").isJsonNull()) {
                            if (builder.length() > 0) {
                                builder.append(" ");
                            }
                            builder.append(blockObj.get("text").getAsString());
                        }
                    }
                    String result = builder.toString().trim();
                    return result.isEmpty() ? null : result;
                }
                if (contentElem.isJsonPrimitive()) {
                    return contentElem.getAsString();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String unescapeJsonString(String raw) {
        if (raw == null || !raw.contains("\\")) {
            return raw;
        }
        try {
            return GSON.fromJson("\"" + raw + "\"", String.class);
        } catch (Exception e) {
            return raw;
        }
    }
}
