package com.protean.copilot.provider.claude;

import com.protean.copilot.cache.ClaudeHistoryIndexStore;
import com.protean.copilot.util.PathUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Scans Claude project history and extracts lightweight session summaries.
 * Supports provider-level persistent indexing with incremental refresh.
 */
class ClaudeHistoryIndexService {

    private final Path projectsDir;
    private final ClaudeHistoryParser parser;
    private final ClaudeSessionLiteReader liteReader;
    private final ClaudeHistoryIndexStore indexStore;
    private final Map<String, ClaudeHistoryIndexStore.ProjectIndex> memoryIndexes = new HashMap<>();

    ClaudeHistoryIndexService(Path projectsDir, ClaudeHistoryParser parser) {
        this(projectsDir, parser, new ClaudeHistoryIndexStore());
    }

    ClaudeHistoryIndexService(Path projectsDir, ClaudeHistoryParser parser, ClaudeHistoryIndexStore indexStore) {
        this.projectsDir = projectsDir;
        this.parser = parser;
        this.liteReader = new ClaudeSessionLiteReader();
        this.indexStore = indexStore;
    }

    List<ClaudeHistoryReader.SessionInfo> readProjectSessions(String projectPath) throws IOException {
        return readProjectSessions(projectPath, false);
    }

    synchronized List<ClaudeHistoryReader.SessionInfo> readProjectSessions(String projectPath, boolean forceRefresh) throws IOException {
        if (projectPath == null || projectPath.isEmpty()) {
            return new ArrayList<>();
        }

        String sanitizedPath = PathUtils.sanitizePath(projectPath);
        Path projectDir = projectsDir.resolve(sanitizedPath);
        if (!Files.exists(projectDir) || !Files.isDirectory(projectDir)) {
            return new ArrayList<>();
        }

        ClaudeHistoryIndexStore.ProjectIndex existing = forceRefresh
            ? null
            : memoryIndexes.computeIfAbsent(projectPath, key -> indexStore.load(key));

        ScanResult scanResult = forceRefresh
            ? fullScan(projectDir)
            : incrementalScanLite(projectDir, existing);

        ClaudeHistoryIndexStore.ProjectIndex persisted = toProjectIndex(projectPath, sanitizedPath, scanResult);
        indexStore.save(persisted);
        memoryIndexes.put(projectPath, persisted);
        return scanResult.sessions();
    }

    ScanResult incrementalScanLite(Path projectDir, ClaudeHistoryIndexStore.ProjectIndex existing) throws IOException {
        Map<String, ClaudeHistoryIndexStore.IndexedSession> existingByFile = new HashMap<>();
        if (existing != null && existing.sessions != null) {
            for (ClaudeHistoryIndexStore.IndexedSession session : existing.sessions) {
                if (session != null && session.fileRelativePath != null) {
                    existingByFile.put(session.fileRelativePath, session);
                }
            }
        }

        List<ClaudeHistoryReader.SessionInfo> sessions = new ArrayList<>();
        Map<String, Long> sessionMtimes = new LinkedHashMap<>();
        Map<String, String> sessionFiles = new LinkedHashMap<>();
        int fileCount = 0;

        try (Stream<Path> paths = Files.list(projectDir)) {
            for (Path path : paths.filter(file -> file.toString().endsWith(".jsonl")).toList()) {
                fileCount++;
                String fileName = path.getFileName().toString();
                long mtime = Files.getLastModifiedTime(path).toMillis();
                long fileSize = Files.size(path);

                ClaudeHistoryIndexStore.IndexedSession cached = existingByFile.get(fileName);
                if (cached != null
                    && cached.fileLastModified == mtime
                    && cached.fileSize == fileSize
                    && cached.entrypoint != null) {
                    ClaudeHistoryReader.SessionInfo restored = restoreCachedSession(cached);
                    if (restored != null) {
                        sessions.add(restored);
                        sessionMtimes.put(restored.sessionId, mtime);
                        sessionFiles.put(restored.sessionId, fileName);
                        continue;
                    }
                }

                ClaudeHistoryReader.SessionInfo reread = readSingleSession(path);
                if (reread != null) {
                    sessions.add(reread);
                    sessionMtimes.put(reread.sessionId, mtime);
                    sessionFiles.put(reread.sessionId, fileName);
                }
            }
        }

        sessions.sort(Comparator.comparingLong((ClaudeHistoryReader.SessionInfo session) -> session.lastTimestamp).reversed());
        return new ScanResult(sessions, sessionMtimes, sessionFiles, fileCount);
    }

    private ScanResult fullScan(Path projectDir) throws IOException {
        return incrementalScanLite(projectDir, null);
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

    private ClaudeHistoryReader.SessionInfo restoreCachedSession(ClaudeHistoryIndexStore.IndexedSession cached) {
        if (cached == null || cached.sessionId == null || cached.title == null || cached.title.isBlank()) {
            return null;
        }

        ClaudeHistoryReader.SessionInfo session = new ClaudeHistoryReader.SessionInfo();
        session.sessionId = cached.sessionId;
        session.title = cached.title;
        session.messageCount = cached.messageCount;
        session.lastTimestamp = cached.lastTimestamp;
        session.firstTimestamp = cached.firstTimestamp;
        session.fileSize = cached.fileSize;
        session.entrypoint = cached.entrypoint.isEmpty() ? null : cached.entrypoint;
        return session;
    }

    private ClaudeHistoryIndexStore.ProjectIndex toProjectIndex(
        String projectPath,
        String sanitizedPath,
        ScanResult scanResult
    ) {
        ClaudeHistoryIndexStore.ProjectIndex index = new ClaudeHistoryIndexStore.ProjectIndex();
        index.projectPath = projectPath;
        index.sanitizedPath = sanitizedPath;
        index.lastScanAt = System.currentTimeMillis();
        index.fileCount = scanResult.fileCount();

        for (ClaudeHistoryReader.SessionInfo session : scanResult.sessions()) {
            if (session == null || session.sessionId == null) {
                continue;
            }
            ClaudeHistoryIndexStore.IndexedSession cached = new ClaudeHistoryIndexStore.IndexedSession();
            cached.sessionId = session.sessionId;
            cached.title = session.title;
            cached.messageCount = session.messageCount;
            cached.lastTimestamp = session.lastTimestamp;
            cached.firstTimestamp = session.firstTimestamp;
            cached.fileSize = session.fileSize;
            cached.entrypoint = session.entrypoint == null ? "" : session.entrypoint;
            cached.fileLastModified = scanResult.sessionMtimes().getOrDefault(session.sessionId, 0L);
            cached.fileRelativePath = scanResult.sessionFiles().get(session.sessionId);
            index.sessions.add(cached);
        }
        return index;
    }

    record ScanResult(
        List<ClaudeHistoryReader.SessionInfo> sessions,
        Map<String, Long> sessionMtimes,
        Map<String, String> sessionFiles,
        int fileCount
    ) {
    }
}
