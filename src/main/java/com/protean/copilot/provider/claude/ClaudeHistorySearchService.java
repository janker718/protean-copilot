package com.protean.copilot.provider.claude;

import com.google.gson.Gson;
import com.intellij.openapi.diagnostic.Logger;
import com.protean.copilot.util.PathUtils;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles project/session retrieval over Claude local history.
 */
class ClaudeHistorySearchService {

    private static final Logger LOG = Logger.getInstance(ClaudeHistorySearchService.class);

    private final Path projectsDir;
    private final ClaudeHistoryIndexService indexService;
    private final Gson gson = new Gson();

    ClaudeHistorySearchService(Path projectsDir, ClaudeHistoryIndexService indexService) {
        this.projectsDir = projectsDir;
        this.indexService = indexService;
    }

    String getProjectDataAsJson(String projectPath) {
        try {
            List<ClaudeHistoryReader.SessionInfo> sessions = indexService.readProjectSessions(projectPath);
            int totalMessages = sessions.stream().mapToInt(session -> session.messageCount).sum();

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("sessions", sessions);
            result.put("currentProject", projectPath);
            result.put("total", totalMessages);
            result.put("sessionCount", sessions.size());
            return gson.toJson(result);
        } catch (Exception e) {
            return gson.toJson(ClaudeHistoryReader.ApiResponse.error("Failed to read project data: " + e.getMessage()));
        }
    }

    String getSessionMessagesAsJson(String projectPath, String sessionId) {
        return gson.toJson(readSessionMessages(projectPath, sessionId));
    }

    List<ClaudeHistoryReader.ConversationMessage> readSessionMessages(String projectPath, String sessionId) {
        List<ClaudeHistoryReader.ConversationMessage> messages = new ArrayList<>();
        try {
            Path sessionFile = resolveSessionFile(projectPath, sessionId);
            if (sessionFile == null || !Files.exists(sessionFile)) {
                return messages;
            }

            try (BufferedReader reader = Files.newBufferedReader(sessionFile, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) {
                        continue;
                    }
                    try {
                        ClaudeHistoryReader.ConversationMessage msg =
                            gson.fromJson(line, ClaudeHistoryReader.ConversationMessage.class);
                        if (msg != null) {
                            messages.add(msg);
                        }
                    } catch (Exception e) {
                        LOG.warn("[ClaudeHistoryReader] Failed to parse message line: " + e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("[ClaudeHistoryReader] Failed to read session messages: " + e.getMessage(), e);
        }
        return messages;
    }

    private Path resolveSessionFile(String projectPath, String sessionId) {
        if (projectPath == null || projectPath.isEmpty() || sessionId == null || sessionId.isEmpty()) {
            return null;
        }
        String sanitizedPath = PathUtils.sanitizePath(projectPath);
        Path projectDir = projectsDir.resolve(sanitizedPath);
        if (!Files.exists(projectDir) || !Files.isDirectory(projectDir)) {
            return null;
        }
        return projectDir.resolve(sessionId + ".jsonl");
    }
}
