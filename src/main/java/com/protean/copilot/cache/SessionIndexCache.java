package com.protean.copilot.cache;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@Service(Service.Level.PROJECT)
public final class SessionIndexCache {

    public static SessionIndexCache getInstance(@NotNull Project project) {
        return project.getService(SessionIndexCache.class);
    }

    private final Map<String, SessionIndexEntry> entries = new LinkedHashMap<>();

    public synchronized void put(@NotNull SessionIndexEntry entry) {
        entries.put(entry.sessionId(), entry);
    }

    public synchronized @Nullable SessionIndexEntry get(@NotNull String sessionId) {
        return entries.get(sessionId);
    }

    public synchronized Collection<SessionIndexEntry> getAll() {
        return new ArrayList<>(entries.values());
    }

    public synchronized void remove(@NotNull String sessionId) {
        entries.remove(sessionId);
    }

    public synchronized void clear() {
        entries.clear();
    }
}
