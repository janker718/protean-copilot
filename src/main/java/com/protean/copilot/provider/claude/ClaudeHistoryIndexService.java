package com.protean.copilot.provider.claude;

import com.protean.copilot.util.PathUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Scans Claude project history and extracts lightweight session summaries.
 */
class ClaudeHistoryIndexService {

    private final Path projectsDir;
    private final ClaudeHistoryParser parser;
    private final ClaudeSessionLiteReader liteReader;

    ClaudeHistoryIndexService(Path projectsDir, ClaudeHistoryParser parser) {
        this.projectsDir = projectsDir;
        this.parser = parser;
        this.liteReader = new ClaudeSessionLiteReader();
    }

    List<ClaudeHistoryReader.SessionInfo> readProjectSessions(String projectPath) throws IOException {
        if (projectPath == null || projectPath.isEmpty()) {
            return new ArrayList<>();
        }

        String sanitizedPath = PathUtils.sanitizePath(projectPath);
        Path projectDir = projectsDir.resolve(sanitizedPath);
        if (!Files.exists(projectDir) || !Files.isDirectory(projectDir)) {
            return new ArrayList<>();
        }

        List<ClaudeHistoryReader.SessionInfo> sessions = new ArrayList<>();
        try (Stream<Path> paths = Files.list(projectDir)) {
            paths
                .filter(path -> path.toString().endsWith(".jsonl"))
                .forEach(path -> {
                    ClaudeHistoryReader.SessionInfo session = readSingleSession(path);
                    if (session != null) {
                        sessions.add(session);
                    }
                });
        }

        sessions.sort(Comparator.comparingLong((ClaudeHistoryReader.SessionInfo session) -> session.lastTimestamp).reversed());
        return sessions;
    }

    private ClaudeHistoryReader.SessionInfo readSingleSession(Path path) {
        ClaudeSessionLiteReader.ClaudeLiteSessionInfo lite = liteReader.readSessionLite(path);
        if (lite != null) {
            ClaudeHistoryReader.SessionInfo session = new ClaudeHistoryReader.SessionInfo();
            session.sessionId = lite.sessionId;
            session.title = lite.customTitle != null && !lite.customTitle.isBlank() ? lite.customTitle : lite.summary;
            session.messageCount = lite.messageCount;
            session.lastTimestamp = lite.lastModified;
            session.firstTimestamp = lite.createdAt;
            session.fileSize = lite.fileSize;
            session.entrypoint = lite.entrypoint;
            return session;
        }
        return parser.scanSingleSession(path);
    }
}
