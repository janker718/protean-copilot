package com.protean.copilot.provider.claude;

import com.google.gson.Gson;
import com.protean.copilot.util.PathUtils;
import com.intellij.openapi.diagnostic.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Claude local history reader.
 */
public class ClaudeHistoryReader {

    private static final Logger LOG = Logger.getInstance(ClaudeHistoryReader.class);

    private static volatile Path claudeDir;

    private static Path claudeDir() {
        Path dir = claudeDir;
        if (dir == null) {
            synchronized (ClaudeHistoryReader.class) {
                dir = claudeDir;
                if (dir == null) {
                    dir = Paths.get(System.getProperty("user.home"), ".claude");
                    claudeDir = dir;
                }
            }
        }
        return dir;
    }

    public static Path projectsDir() {
        return claudeDir().resolve("projects");
    }

    public static Path projectDir(String projectPath) {
        return projectsDir().resolve(PathUtils.sanitizePath(projectPath));
    }

    private final Gson gson = new Gson();
    private final ClaudeHistoryIndexService indexService;
    private final ClaudeHistorySearchService searchService;

    public ClaudeHistoryReader() {
        Path projectsDir = projectsDir();
        ClaudeHistoryParser parser = new ClaudeHistoryParser();
        this.indexService = new ClaudeHistoryIndexService(projectsDir, parser);
        this.searchService = new ClaudeHistorySearchService(projectsDir, indexService);
    }

    public static class ConversationMessage {
        public String uuid;
        public String sessionId;
        public String parentUuid;
        public String timestamp;
        public String type;
        public Message message;
        public Boolean isMeta;
        public Boolean isSidechain;
        public String cwd;
        public String entrypoint;

        public static class Message {
            public String role;
            public Object content;
            public Usage usage;
        }

        public static class Usage {
            public int input_tokens;
            public int output_tokens;
            public int cache_creation_input_tokens;
            public int cache_read_input_tokens;
        }
    }

    public static class SessionInfo {
        public String sessionId;
        public String title;
        public int messageCount;
        public long lastTimestamp;
        public long firstTimestamp;
        public long fileSize;
        public String entrypoint;
    }

    public static class ApiResponse {
        public boolean success;
        public String error;
        public Object data;

        public static ApiResponse error(String message) {
            ApiResponse response = new ApiResponse();
            response.success = false;
            response.error = message;
            return response;
        }
    }

    public List<SessionInfo> readProjectSessions(String projectPath) {
        try {
            return indexService.readProjectSessions(projectPath);
        } catch (Exception e) {
            LOG.warn("Failed to read Claude project sessions: " + e.getMessage(), e);
            return List.of();
        }
    }

    public String getProjectDataAsJson(String projectPath) {
        return searchService.getProjectDataAsJson(projectPath);
    }

    public String getSessionMessagesAsJson(String projectPath, String sessionId) {
        return searchService.getSessionMessagesAsJson(projectPath, sessionId);
    }

    public List<ConversationMessage> readSessionMessages(String projectPath, String sessionId) {
        return searchService.readSessionMessages(projectPath, sessionId);
    }
}
