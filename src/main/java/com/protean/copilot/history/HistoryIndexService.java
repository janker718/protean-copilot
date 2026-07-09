package com.protean.copilot.history;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.protean.copilot.cache.SessionIndexCache;
import com.protean.copilot.cache.SessionIndexEntry;
import com.protean.copilot.provider.claude.ClaudeHistoryReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service(Service.Level.PROJECT)
public final class HistoryIndexService {

    public static HistoryIndexService getInstance(@NotNull Project project) {
        return project.getService(HistoryIndexService.class);
    }

    private final Project project;

    public HistoryIndexService(Project project) {
        this.project = project;
    }

    public @NotNull Collection<SessionIndexEntry> listEntries() {
        return listEntries(resolveProjectPath(null), null);
    }

    public @NotNull List<SessionIndexEntry> listEntries(@Nullable String projectPath, @Nullable String providerFilter) {
        String normalizedProjectPath = resolveProjectPath(projectPath);
        String normalizedProvider = normalize(providerFilter);
        Map<String, SessionIndexEntry> merged = new LinkedHashMap<>();

        for (SessionIndexEntry runtimeEntry : SessionIndexCache.getInstance(project).getAll()) {
            if (matchesProvider(runtimeEntry, normalizedProvider) && matchesProject(runtimeEntry, normalizedProjectPath)) {
                merged.put(runtimeEntry.sessionId(), runtimeEntry);
            }
        }

        if (normalizedProvider == null || "claude".equals(normalizedProvider)) {
            mergeClaudeHistoryEntries(merged, normalizedProjectPath);
        }

        List<SessionIndexEntry> sorted = new ArrayList<>(merged.values());
        sorted.sort(Comparator.comparingLong(SessionIndexEntry::updatedAt).reversed());
        return sorted;
    }

    public @Nullable SessionIndexEntry getEntry(@NotNull String sessionId) {
        SessionIndexEntry runtimeEntry = SessionIndexCache.getInstance(project).get(sessionId);
        if (runtimeEntry != null) {
            return runtimeEntry;
        }
        return getEntry(sessionId, null, null);
    }

    public @Nullable SessionIndexEntry getEntry(
        @NotNull String sessionId,
        @Nullable String projectPath,
        @Nullable String providerFilter
    ) {
        String normalizedProjectPath = resolveProjectPath(projectPath);
        String normalizedProvider = normalize(providerFilter);

        SessionIndexEntry runtimeEntry = SessionIndexCache.getInstance(project).get(sessionId);
        if (runtimeEntry != null
            && matchesProvider(runtimeEntry, normalizedProvider)
            && matchesProject(runtimeEntry, normalizedProjectPath)) {
            return runtimeEntry;
        }

        for (SessionIndexEntry entry : listEntries(normalizedProjectPath, normalizedProvider)) {
            if (sessionId.equals(entry.sessionId())) {
                return entry;
            }
        }
        return null;
    }

    public void remove(@NotNull String sessionId) {
        SessionIndexCache.getInstance(project).remove(sessionId);
    }

    private void mergeClaudeHistoryEntries(Map<String, SessionIndexEntry> merged, @Nullable String projectPath) {
        if (projectPath == null) {
            return;
        }

        ClaudeHistoryReader historyReader = new ClaudeHistoryReader();
        for (ClaudeHistoryReader.SessionInfo session : historyReader.readProjectSessions(projectPath)) {
            if (session == null || normalize(session.sessionId) == null) {
                continue;
            }

            SessionIndexEntry runtimeEntry = merged.get(session.sessionId);
            SessionIndexEntry mergedEntry = mergeClaudeSession(runtimeEntry, session, projectPath);
            merged.put(mergedEntry.sessionId(), mergedEntry);
            SessionIndexCache.getInstance(project).put(mergedEntry);
        }
    }

    private SessionIndexEntry mergeClaudeSession(
        @Nullable SessionIndexEntry runtimeEntry,
        @NotNull ClaudeHistoryReader.SessionInfo session,
        @NotNull String projectPath
    ) {
        String summary = firstNonBlank(
            runtimeEntry != null ? runtimeEntry.summary() : null,
            session.title,
            "Untitled session"
        );
        String provider = firstNonBlank(runtimeEntry != null ? runtimeEntry.provider() : null, "claude");
        String workingDirectory = firstNonBlank(
            runtimeEntry != null ? runtimeEntry.workingDirectory() : null,
            projectPath
        );
        long updatedAt = Math.max(session.lastTimestamp, runtimeEntry != null ? runtimeEntry.updatedAt() : 0L);
        int messageCount = session.messageCount > 0
            ? session.messageCount
            : runtimeEntry != null ? runtimeEntry.messageCount() : 0;
        long fileSize = session.fileSize > 0
            ? session.fileSize
            : runtimeEntry != null ? runtimeEntry.fileSize() : 0L;
        boolean favorited = runtimeEntry != null && runtimeEntry.favorited();
        long favoritedAt = runtimeEntry != null ? runtimeEntry.favoritedAt() : 0L;
        String customTitle = runtimeEntry != null ? runtimeEntry.customTitle() : null;
        String entrypoint = firstNonBlank(
            runtimeEntry != null ? runtimeEntry.entrypoint() : null,
            session.entrypoint,
            "sdk-cli"
        );

        return new SessionIndexEntry(
            session.sessionId,
            summary,
            provider,
            workingDirectory,
            updatedAt,
            messageCount,
            fileSize,
            favorited,
            favoritedAt,
            customTitle,
            entrypoint
        );
    }

    private @Nullable String resolveProjectPath(@Nullable String projectPath) {
        String candidate = normalize(projectPath);
        if (candidate != null) {
            return candidate;
        }
        return normalize(project.getBasePath());
    }

    private boolean matchesProvider(@NotNull SessionIndexEntry entry, @Nullable String providerFilter) {
        if (providerFilter == null) {
            return true;
        }
        return providerFilter.equals(normalize(entry.provider()));
    }

    private boolean matchesProject(@NotNull SessionIndexEntry entry, @Nullable String projectPath) {
        if (projectPath == null) {
            return true;
        }
        String workingDirectory = normalize(entry.workingDirectory());
        if (workingDirectory == null) {
            return false;
        }

        String normalizedEntryPath = normalizePath(workingDirectory);
        String normalizedProjectPath = normalizePath(projectPath);
        if (normalizedEntryPath == null || normalizedProjectPath == null) {
            return Objects.equals(workingDirectory, projectPath);
        }

        return normalizedEntryPath.equals(normalizedProjectPath)
            || normalizedEntryPath.startsWith(normalizedProjectPath + "/");
    }

    private static @Nullable String normalizePath(@Nullable String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        try {
            return Path.of(normalized).normalize().toString().replace('\\', '/');
        } catch (InvalidPathException ignored) {
            return normalized.replace('\\', '/');
        }
    }

    private static @Nullable String normalize(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static @NotNull String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return "";
    }
}
