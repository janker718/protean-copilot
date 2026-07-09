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
    private final ClaudeUsageAggregator usageAggregator;
    private final ClaudeHistorySearchService searchService;

    public ClaudeHistoryReader() {
        Path projectsDir = projectsDir();
        ClaudeHistoryParser parser = new ClaudeHistoryParser();
        this.indexService = new ClaudeHistoryIndexService(projectsDir, parser);
        this.usageAggregator = new ClaudeUsageAggregator(projectsDir, parser);
        this.searchService = new ClaudeHistorySearchService(projectsDir, indexService, usageAggregator);
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
            public String model;
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

    public static class UsageData {
        public long inputTokens;
        public long outputTokens;
        public long cacheWriteTokens;
        public long cacheReadTokens;
        public long totalTokens;
    }

    public static class SessionSummary {
        public String sessionId;
        public long timestamp;
        public String model;
        public UsageData usage;
        public double cost;
        public String summary;
    }

    public static class DailyUsage {
        public String date;
        public int sessions;
        public UsageData usage;
        public double cost;
        public List<String> modelsUsed;
    }

    public static class ModelUsage {
        public String model;
        public double totalCost;
        public long totalTokens;
        public long inputTokens;
        public long outputTokens;
        public long cacheCreationTokens;
        public long cacheReadTokens;
        public int sessionCount;
    }

    public static class WeeklyComparison {
        public WeekData currentWeek;
        public WeekData lastWeek;
        public Trends trends;

        public static class WeekData {
            public int sessions;
            public double cost;
            public long tokens;
        }

        public static class Trends {
            public double sessions;
            public double cost;
            public double tokens;
        }
    }

    public static class ProjectStatistics {
        public String projectPath;
        public String projectName;
        public int totalSessions;
        public UsageData totalUsage;
        public double estimatedCost;
        public List<SessionSummary> sessions;
        public List<DailyUsage> dailyUsage;
        public WeeklyComparison weeklyComparison;
        public List<ModelUsage> byModel;
        public long lastUpdated;
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

        public static ApiResponse success(Object data) {
            ApiResponse response = new ApiResponse();
            response.success = true;
            response.data = data;
            return response;
        }
    }

    public List<SessionInfo> readProjectSessions(String projectPath) {
        return readProjectSessions(projectPath, false);
    }

    public List<SessionInfo> readProjectSessions(String projectPath, boolean forceRefresh) {
        try {
            return indexService.readProjectSessions(projectPath, forceRefresh);
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

    public ProjectStatistics getProjectStatistics(String projectPath, long cutoffTime) {
        return usageAggregator.getProjectStatistics(projectPath, cutoffTime);
    }

    public String getProjectStatisticsAsJson(String projectPath, long cutoffTime) {
        return searchService.getProjectStatisticsAsJson(projectPath, cutoffTime);
    }

    public List<ConversationMessage> readSessionMessages(String projectPath, String sessionId) {
        return searchService.readSessionMessages(projectPath, sessionId);
    }
}
