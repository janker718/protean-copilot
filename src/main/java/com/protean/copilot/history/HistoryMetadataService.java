package com.protean.copilot.history;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.protean.copilot.cache.SessionIndexCache;
import com.protean.copilot.cache.SessionIndexEntry;
import com.protean.copilot.session.ChatSession;
import org.jetbrains.annotations.NotNull;

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
        SessionIndexCache.getInstance(project).put(new SessionIndexEntry(
            sessionId,
            session.getSummary(),
            session.getProvider(),
            session.getCwd(),
            session.getLastModifiedTime()
        ));
    }
}
