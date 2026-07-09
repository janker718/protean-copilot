package com.protean.copilot.history;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.protean.copilot.cache.SessionIndexCache;
import com.protean.copilot.cache.SessionIndexEntry;
import com.protean.copilot.provider.claude.ClaudeHistorySource;
import com.protean.copilot.provider.codex.CodexHistorySource;
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
    private final List<ProviderHistorySource> providerHistorySources;

    public HistoryIndexService(Project project) {
        this.project = project;
        this.providerHistorySources = List.of(
            new ClaudeHistorySource(),
            new CodexHistorySource()
        );
    }

    public @NotNull Collection<SessionIndexEntry> listEntries() {
        return listEntries(resolveProjectPath(null), null, false);
    }

    public @NotNull List<SessionIndexEntry> listEntries(@Nullable String projectPath, @Nullable String providerFilter) {
        return listEntries(projectPath, providerFilter, false);
    }

    public @NotNull List<SessionIndexEntry> listEntries(
        @Nullable String projectPath,
        @Nullable String providerFilter,
        boolean forceRefresh
    ) {
        String normalizedProjectPath = resolveProjectPath(projectPath);
        String normalizedProvider = normalize(providerFilter);
        Map<String, SessionIndexEntry> merged = new LinkedHashMap<>();

        for (SessionIndexEntry runtimeEntry : SessionIndexCache.getInstance(project).getAll()) {
            if (matchesProvider(runtimeEntry, normalizedProvider) && matchesProject(runtimeEntry, normalizedProjectPath)) {
                merged.put(runtimeEntry.sessionId(), runtimeEntry);
            }
        }

        for (ProviderHistorySource source : providerHistorySources) {
            if (normalizedProvider == null || normalizedProvider.equals(normalize(source.providerId()))) {
                mergeProviderEntries(merged, source, normalizedProjectPath, forceRefresh);
            }
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

    private void mergeProviderEntries(
        Map<String, SessionIndexEntry> merged,
        @NotNull ProviderHistorySource source,
        @Nullable String projectPath,
        boolean forceRefresh
    ) {
        if (projectPath == null) {
            return;
        }

        for (SessionIndexEntry providerEntry : source.listEntries(projectPath, forceRefresh)) {
            if (providerEntry == null || normalize(providerEntry.sessionId()) == null) {
                continue;
            }

            SessionIndexEntry runtimeEntry = merged.get(providerEntry.sessionId());
            SessionIndexEntry mergedEntry = mergeProviderEntry(runtimeEntry, providerEntry, projectPath, source.providerId());
            merged.put(mergedEntry.sessionId(), mergedEntry);
        }
    }

    private SessionIndexEntry mergeProviderEntry(
        @Nullable SessionIndexEntry runtimeEntry,
        @NotNull SessionIndexEntry providerEntry,
        @NotNull String projectPath,
        @NotNull String providerId
    ) {
        String summary = firstNonBlank(
            runtimeEntry != null ? runtimeEntry.summary() : null,
            providerEntry.summary(),
            "Untitled session"
        );
        String provider = firstNonBlank(runtimeEntry != null ? runtimeEntry.provider() : null, providerEntry.provider(), providerId);
        String workingDirectory = firstNonBlank(
            runtimeEntry != null ? runtimeEntry.workingDirectory() : null,
            providerEntry.workingDirectory(),
            projectPath
        );
        long updatedAt = Math.max(providerEntry.updatedAt(), runtimeEntry != null ? runtimeEntry.updatedAt() : 0L);
        int messageCount = providerEntry.messageCount() > 0
            ? providerEntry.messageCount()
            : runtimeEntry != null ? runtimeEntry.messageCount() : 0;
        long fileSize = providerEntry.fileSize() > 0
            ? providerEntry.fileSize()
            : runtimeEntry != null ? runtimeEntry.fileSize() : 0L;
        boolean favorited = runtimeEntry != null && runtimeEntry.favorited();
        long favoritedAt = runtimeEntry != null ? runtimeEntry.favoritedAt() : 0L;
        String customTitle = runtimeEntry != null ? runtimeEntry.customTitle() : null;
        String entrypoint = firstNonBlank(
            runtimeEntry != null ? runtimeEntry.entrypoint() : null,
            providerEntry.entrypoint(),
            "sdk-cli"
        );

        return new SessionIndexEntry(
            providerEntry.sessionId(),
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
