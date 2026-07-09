package com.protean.copilot.provider.claude;

import com.protean.copilot.provider.common.SessionLiteReader;

import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Claude-specific lite session reader.
 */
public class ClaudeSessionLiteReader {

    private static final Pattern UUID_PATTERN = Pattern.compile(
        "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
        Pattern.CASE_INSENSITIVE
    );

    private final SessionLiteReader liteReader = new SessionLiteReader();

    public static class ClaudeLiteSessionInfo {
        public final String sessionId;
        public final String summary;
        public final long lastModified;
        public final long fileSize;
        public final String customTitle;
        public final String firstPrompt;
        public final int messageCount;
        public final long createdAt;
        public final String entrypoint;

        public ClaudeLiteSessionInfo(
            String sessionId,
            String summary,
            long lastModified,
            long fileSize,
            String customTitle,
            String firstPrompt,
            int messageCount,
            long createdAt,
            String entrypoint
        ) {
            this.sessionId = sessionId;
            this.summary = summary;
            this.lastModified = lastModified;
            this.fileSize = fileSize;
            this.customTitle = customTitle;
            this.firstPrompt = firstPrompt;
            this.messageCount = messageCount;
            this.createdAt = createdAt;
            this.entrypoint = entrypoint;
        }
    }

    public ClaudeLiteSessionInfo readSessionLite(Path sessionPath) {
        SessionLiteReader.LiteSessionFile lite = liteReader.readSessionLite(sessionPath);
        if (lite == null) {
            return null;
        }

        String sessionId = extractSessionId(sessionPath.getFileName().toString());
        if (sessionId == null || liteReader.isSidechainSession(lite.head)) {
            return null;
        }
        return parseSessionInfoFromLite(sessionId, lite);
    }

    public ClaudeLiteSessionInfo parseSessionInfoFromLite(String sessionId, SessionLiteReader.LiteSessionFile lite) {
        if (lite == null || sessionId == null) {
            return null;
        }

        String userTitle = liteReader.extractLastJsonStringField(lite.tail, "customTitle");
        if (userTitle == null) {
            userTitle = liteReader.extractLastJsonStringField(lite.head, "customTitle");
        }
        if (userTitle == null) {
            userTitle = liteReader.extractLastJsonStringField(lite.tail, "aiTitle");
        }
        if (userTitle == null) {
            userTitle = liteReader.extractLastJsonStringField(lite.head, "aiTitle");
        }

        String firstPrompt = liteReader.extractFirstPromptFromHead(lite.head);
        String lastPrompt = liteReader.extractLastJsonStringField(lite.tail, "lastPrompt");
        String summary = userTitle;
        if (summary == null) {
            summary = lastPrompt;
        }
        if (summary == null) {
            summary = liteReader.extractLastJsonStringField(lite.tail, "summary");
        }
        if (summary == null) {
            summary = firstPrompt;
        }
        if (summary == null || summary.isEmpty() || !isValidSession(sessionId, summary)) {
            return null;
        }

        String entrypoint = liteReader.extractJsonStringField(lite.head, "entrypoint");
        String firstTimestamp = liteReader.extractJsonStringField(lite.head, "timestamp");
        long createdAt = firstTimestamp == null ? 0 : parseTimestamp(firstTimestamp);

        return new ClaudeLiteSessionInfo(
            sessionId,
            summary,
            lite.mtime,
            lite.size,
            userTitle,
            firstPrompt,
            liteReader.countMessagesInHead(lite.head),
            createdAt,
            entrypoint
        );
    }

    public SessionLiteReader getLiteReader() {
        return liteReader;
    }

    private String extractSessionId(String fileName) {
        if (!fileName.endsWith(".jsonl")) {
            return null;
        }
        String sessionId = fileName.substring(0, fileName.length() - 6);
        if (!UUID_PATTERN.matcher(sessionId).matches()) {
            return null;
        }
        return sessionId.toLowerCase();
    }

    private boolean isValidSession(String sessionId, String summary) {
        if (sessionId != null && sessionId.startsWith("agent-")) {
            return false;
        }
        String lowerSummary = summary == null ? "" : summary.toLowerCase();
        return !lowerSummary.isEmpty()
            && !lowerSummary.equals("warmup")
            && !lowerSummary.equals("no prompt")
            && !lowerSummary.startsWith("warmup")
            && !lowerSummary.startsWith("no prompt");
    }

    private long parseTimestamp(String timestamp) {
        try {
            return java.time.Instant.parse(timestamp).toEpochMilli();
        } catch (Exception ignored) {
            return 0;
        }
    }
}
