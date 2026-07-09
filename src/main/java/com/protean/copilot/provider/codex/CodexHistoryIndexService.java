package com.protean.copilot.provider.codex;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

final class CodexHistoryIndexService {

    private static final Logger LOG = Logger.getInstance(CodexHistoryIndexService.class);

    private final Path sessionsDir;
    private final CodexHistoryParser parser;

    CodexHistoryIndexService(@NotNull Path sessionsDir, @NotNull CodexHistoryParser parser) {
        this.sessionsDir = sessionsDir;
        this.parser = parser;
    }

    @NotNull List<CodexHistoryReader.SessionInfo> readProjectSessions(@Nullable String projectPath, boolean forceRefresh) {
        if (projectPath == null || projectPath.isBlank()) {
            return List.of();
        }
        if (!Files.isDirectory(sessionsDir)) {
            return List.of();
        }

        List<CodexHistoryReader.SessionInfo> sessions = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(sessionsDir)) {
            paths.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".jsonl"))
                .forEach(path -> addIfMatching(sessions, projectPath, path));
        } catch (IOException ex) {
            LOG.warn("Failed to scan Codex sessions: " + ex.getMessage(), ex);
            return List.of();
        }

        sessions.sort(Comparator.comparingLong(CodexHistoryReader.SessionInfo::lastTimestamp).reversed());
        return sessions;
    }

    @Nullable Path findSessionFile(@NotNull String sessionId, @Nullable String cwd) {
        if (!Files.isDirectory(sessionsDir)) {
            return null;
        }

        try (Stream<Path> paths = Files.walk(sessionsDir)) {
            return paths.filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".jsonl"))
                .filter(path -> matchesSession(path, sessionId, cwd))
                .findFirst()
                .orElse(null);
        } catch (IOException ex) {
            LOG.warn("Failed to locate Codex session file: " + ex.getMessage(), ex);
            return null;
        }
    }

    private void addIfMatching(@NotNull List<CodexHistoryReader.SessionInfo> sessions, @NotNull String projectPath, @NotNull Path path) {
        try {
            CodexHistoryReader.SessionInfo session = parser.parseSessionFile(path);
            if (session.cwd == null || session.cwd.isBlank()) {
                return;
            }
            if (!matchesProject(session.cwd, projectPath)) {
                return;
            }
            sessions.add(session);
        } catch (IOException ex) {
            LOG.debug("Failed to parse Codex session file " + path + ": " + ex.getMessage());
        }
    }

    private boolean matchesSession(@NotNull Path path, @NotNull String sessionId, @Nullable String cwd) {
        String fileName = path.getFileName().toString();
        if (fileName.equals(sessionId + ".jsonl") || fileName.endsWith("-" + sessionId + ".jsonl")) {
            return true;
        }
        try {
            CodexHistoryReader.SessionInfo session = parser.parseSessionFile(path);
            if (!sessionId.equals(session.sessionId)) {
                return false;
            }
            return cwd == null || cwd.isBlank() || matchesProject(session.cwd, cwd);
        } catch (IOException ex) {
            LOG.debug("Failed to inspect Codex session file " + path + ": " + ex.getMessage());
            return false;
        }
    }

    private static boolean matchesProject(@Nullable String cwd, @NotNull String projectPath) {
        String normalizedCwd = normalizePath(cwd);
        String normalizedProjectPath = normalizePath(projectPath);
        if (normalizedCwd == null || normalizedProjectPath == null) {
            return false;
        }
        return normalizedCwd.equals(normalizedProjectPath)
            || normalizedCwd.startsWith(normalizedProjectPath + "/");
    }

    private static @Nullable String normalizePath(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Path.of(value).normalize().toString().replace('\\', '/');
    }
}
