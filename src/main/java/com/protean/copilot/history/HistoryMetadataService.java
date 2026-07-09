package com.protean.copilot.history;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.protean.copilot.cache.SessionIndexCache;
import com.protean.copilot.cache.SessionIndexEntry;
import com.protean.copilot.session.ChatSession;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.PROJECT)
public final class HistoryMetadataService {

    public static HistoryMetadataService getInstance(@NotNull Project project) {
        return project.getService(HistoryMetadataService.class);
    }

    private final Project project;

    public HistoryMetadataService(Project project) {
        this.project = project;
    }

    public void updateFromSession(@NotNull ChatSession session) {
        String sessionId = session.ensureSessionId();
        SessionIndexEntry existing = SessionIndexCache.getInstance(project).get(sessionId);
        SessionIndexCache.getInstance(project).put(new SessionIndexEntry(
            sessionId,
            session.getSummary(),
            session.getProvider(),
            session.getCwd(),
            session.getLastModifiedTime(),
            existing != null && existing.favorited(),
            existing != null ? existing.favoritedAt() : 0L,
            existing != null ? existing.customTitle() : null,
            existing != null && existing.entrypoint() != null ? existing.entrypoint() : "sdk-cli"
        ));
    }

    public void toggleFavorite(@NotNull String sessionId) {
        SessionIndexEntry entry = SessionIndexCache.getInstance(project).get(sessionId);
        if (entry == null) {
            return;
        }
        boolean nextFavorited = !entry.favorited();
        long favoritedAt = nextFavorited ? System.currentTimeMillis() : 0L;
        SessionIndexCache.getInstance(project).put(copyOf(entry, nextFavorited, favoritedAt, entry.customTitle(), entry.entrypoint()));
    }

    public void updateCustomTitle(@NotNull String sessionId, @Nullable String customTitle) {
        SessionIndexEntry entry = SessionIndexCache.getInstance(project).get(sessionId);
        if (entry == null) {
            return;
        }
        String normalizedTitle = normalizeTitle(customTitle);
        SessionIndexCache.getInstance(project).put(copyOf(entry, entry.favorited(), entry.favoritedAt(), normalizedTitle, entry.entrypoint()));
    }

    public void deleteCustomTitle(@NotNull String sessionId) {
        updateCustomTitle(sessionId, null);
    }

    public boolean updateEntrypoint(@NotNull String sessionId, @Nullable String entrypoint) {
        SessionIndexEntry entry = SessionIndexCache.getInstance(project).get(sessionId);
        if (entry == null) {
            return false;
        }
        SessionIndexCache.getInstance(project).put(copyOf(
            entry,
            entry.favorited(),
            entry.favoritedAt(),
            entry.customTitle(),
            normalizeEntrypoint(entrypoint)
        ));
        return true;
    }

    private static SessionIndexEntry copyOf(
        @NotNull SessionIndexEntry entry,
        boolean favorited,
        long favoritedAt,
        @Nullable String customTitle,
        @Nullable String entrypoint
    ) {
        return new SessionIndexEntry(
            entry.sessionId(),
            entry.summary(),
            entry.provider(),
            entry.workingDirectory(),
            entry.updatedAt(),
            favorited,
            favoritedAt,
            customTitle,
            normalizeEntrypoint(entrypoint)
        );
    }

    private static @Nullable String normalizeTitle(@Nullable String customTitle) {
        if (customTitle == null) {
            return null;
        }
        String trimmed = customTitle.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeEntrypoint(@Nullable String entrypoint) {
        if (entrypoint == null || entrypoint.isBlank()) {
            return "sdk-cli";
        }
        return entrypoint.trim();
    }
}
