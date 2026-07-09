package com.protean.copilot.provider.claude;

import com.google.gson.Gson;
import com.intellij.openapi.diagnostic.Logger;
import com.protean.copilot.util.TagExtractor;
import com.protean.copilot.util.TextSanitizer;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Handles parsing of Claude session JSONL files.
 */
class ClaudeHistoryParser {

    private static final Logger LOG = Logger.getInstance(ClaudeHistoryParser.class);

    private final Gson gson = new Gson();
    private final BiConsumer<Path, Exception> scanFailureReporter;

    ClaudeHistoryParser() {
        this(ClaudeHistoryParser::logRecoverableScanFailure);
    }

    ClaudeHistoryParser(BiConsumer<Path, Exception> scanFailureReporter) {
        this.scanFailureReporter = scanFailureReporter;
    }

    ClaudeHistoryReader.SessionInfo scanSingleSession(Path path) {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String fileName = path.getFileName().toString();
            String sessionId = fileName.substring(0, fileName.lastIndexOf(".jsonl"));

            List<ClaudeHistoryReader.ConversationMessage> messages = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                try {
                    ClaudeHistoryReader.ConversationMessage msg = gson.fromJson(line, ClaudeHistoryReader.ConversationMessage.class);
                    if (msg != null) {
                        messages.add(msg);
                    }
                } catch (Exception ignored) {
                }
            }

            if (messages.isEmpty()) {
                return null;
            }

            String summary = generateSummary(messages);
            long firstTimestamp = 0;
            long lastTimestamp = 0;
            for (ClaudeHistoryReader.ConversationMessage msg : messages) {
                if (msg.timestamp == null) {
                    continue;
                }
                long ts = parseTimestamp(msg.timestamp);
                if (ts <= 0) {
                    continue;
                }
                if (firstTimestamp == 0 || ts < firstTimestamp) {
                    firstTimestamp = ts;
                }
                if (ts > lastTimestamp) {
                    lastTimestamp = ts;
                }
            }

            if (!isValidSession(sessionId, summary, messages.size())) {
                return null;
            }

            ClaudeHistoryReader.SessionInfo session = new ClaudeHistoryReader.SessionInfo();
            session.sessionId = sessionId;
            session.title = summary;
            session.messageCount = messages.size();
            session.lastTimestamp = lastTimestamp;
            session.firstTimestamp = firstTimestamp;
            session.fileSize = Files.size(path);
            session.entrypoint = sniffEntrypoint(messages);
            return session;
        } catch (Exception e) {
            scanFailureReporter.accept(path, e);
            return null;
        }
    }

    String generateSummary(List<ClaudeHistoryReader.ConversationMessage> messages) {
        for (ClaudeHistoryReader.ConversationMessage msg : messages) {
            if (!"user".equals(msg.type)
                || Boolean.TRUE.equals(msg.isMeta)
                || msg.message == null
                || msg.message.content == null) {
                continue;
            }
            String text = extractTextFromContent(msg.message.content);
            if (text != null && !text.isEmpty()) {
                text = TagExtractor.extractCommandMessageContent(text);
                return TextSanitizer.sanitizeAndTruncateSingleLine(text, 45);
            }
        }
        return null;
    }

    boolean isValidSession(String sessionId, String summary, int messageCount) {
        if (sessionId != null && sessionId.startsWith("agent-")) {
            return false;
        }
        if (summary == null || summary.isEmpty()) {
            return false;
        }
        String lowerSummary = summary.toLowerCase();
        if (lowerSummary.equals("warmup")
            || lowerSummary.equals("no prompt")
            || lowerSummary.startsWith("warmup")
            || lowerSummary.startsWith("no prompt")) {
            return false;
        }
        return messageCount >= 2;
    }

    String extractTextFromContent(Object content) {
        if (content instanceof String stringContent) {
            return stringContent;
        }
        if (content instanceof List<?> contentList) {
            StringBuilder builder = new StringBuilder();
            for (Object itemObj : contentList) {
                if (!(itemObj instanceof Map<?, ?> item)) {
                    continue;
                }
                Object type = item.get("type");
                if (!"text".equals(type)) {
                    continue;
                }
                Object text = item.get("text");
                if (text instanceof String textValue) {
                    if (builder.length() > 0) {
                        builder.append(" ");
                    }
                    builder.append(textValue);
                }
            }
            String result = builder.toString().trim();
            return result.isEmpty() ? null : result;
        }
        if (content instanceof com.google.gson.JsonArray contentArray) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < contentArray.size(); i++) {
                com.google.gson.JsonElement element = contentArray.get(i);
                if (!element.isJsonObject()) {
                    continue;
                }
                com.google.gson.JsonObject item = element.getAsJsonObject();
                String type = item.has("type") && !item.get("type").isJsonNull()
                    ? item.get("type").getAsString()
                    : null;
                if ("text".equals(type) && item.has("text") && !item.get("text").isJsonNull()) {
                    if (builder.length() > 0) {
                        builder.append(" ");
                    }
                    builder.append(item.get("text").getAsString());
                }
            }
            String result = builder.toString().trim();
            return result.isEmpty() ? null : result;
        }
        return null;
    }

    long parseTimestamp(String timestamp) {
        try {
            return java.time.Instant.parse(timestamp).toEpochMilli();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static void logRecoverableScanFailure(Path path, Exception e) {
        if (e instanceof NoSuchFileException) {
            LOG.debug("[ClaudeHistoryReader] Session disappeared during scan: " + path);
            return;
        }
        LOG.warn("[ClaudeHistoryReader] Skipping unreadable session during scan: " + path + " (" + e.getMessage() + ")");
    }

    private static String sniffEntrypoint(List<ClaudeHistoryReader.ConversationMessage> messages) {
        for (ClaudeHistoryReader.ConversationMessage msg : messages) {
            if (msg.entrypoint != null && !msg.entrypoint.isEmpty()) {
                return msg.entrypoint;
            }
        }
        return null;
    }
}
