package com.protean.copilot.history;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.protean.copilot.cache.SessionIndexCache;
import com.protean.copilot.cache.SessionIndexEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

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
        return SessionIndexCache.getInstance(project).getAll();
    }

    public @Nullable SessionIndexEntry getEntry(@NotNull String sessionId) {
        return SessionIndexCache.getInstance(project).get(sessionId);
    }

    public void remove(@NotNull String sessionId) {
        SessionIndexCache.getInstance(project).remove(sessionId);
    }
}
