package com.protean.copilot.permission;

import com.intellij.openapi.diagnostic.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Tracks PermissionService instances by session id and cleans up stale sessions.
 */
final class PermissionSessionRegistry {

    private static final Logger LOG = Logger.getInstance(PermissionSessionRegistry.class);
    private static final long SESSION_CLEANUP_INTERVAL_MS = TimeUnit.HOURS.toMillis(1);
    private static final long SESSION_MAX_IDLE_TIME_MS = TimeUnit.HOURS.toMillis(24);

    private static PermissionService legacyInstance;
    private static final Map<String, PermissionService> INSTANCES_BY_SESSION_ID = new ConcurrentHashMap<>();
    private static volatile long lastCleanupTime = System.currentTimeMillis();

    private PermissionSessionRegistry() {
    }

    static synchronized PermissionService getInstance(
        String sessionId,
        Supplier<PermissionService> legacyFactory,
        Function<String, PermissionService> sessionFactory
    ) {
        cleanupStaleInstancesIfNeeded();

        if (sessionId == null || sessionId.isEmpty()) {
            if (legacyInstance == null) {
                legacyInstance = legacyFactory.get();
            }
            return legacyInstance;
        }

        return INSTANCES_BY_SESSION_ID.computeIfAbsent(sessionId, sessionFactory);
    }

    static synchronized PermissionService getLegacyInstance(Supplier<PermissionService> legacyFactory) {
        if (legacyInstance == null) {
            legacyInstance = legacyFactory.get();
        }
        return legacyInstance;
    }

    static synchronized void removeInstance(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        PermissionService removed = INSTANCES_BY_SESSION_ID.remove(sessionId);
        if (removed != null) {
            removed.stop();
            LOG.info("PermissionService instance removed for sessionId=" + sessionId);
        }
    }

    static synchronized PermissionService findAnyInstanceForProject(com.intellij.openapi.project.Project project) {
        if (project == null) {
            return null;
        }
        PermissionService newestMatch = null;
        long newestActivityTime = Long.MIN_VALUE;
        for (PermissionService service : INSTANCES_BY_SESSION_ID.values()) {
            if (service.getProject() == project && service.getLastActivityTime() >= newestActivityTime) {
                newestMatch = service;
                newestActivityTime = service.getLastActivityTime();
            }
        }
        if (legacyInstance != null && legacyInstance.getProject() == project
            && legacyInstance.getLastActivityTime() >= newestActivityTime) {
            newestMatch = legacyInstance;
        }
        return newestMatch;
    }

    private static synchronized void cleanupStaleInstancesIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCleanupTime < SESSION_CLEANUP_INTERVAL_MS) {
            return;
        }
        lastCleanupTime = now;
        List<String> sessionsToRemove = new ArrayList<>();
        for (Map.Entry<String, PermissionService> entry : INSTANCES_BY_SESSION_ID.entrySet()) {
            PermissionService service = entry.getValue();
            if (now - service.getLastActivityTime() > SESSION_MAX_IDLE_TIME_MS) {
                sessionsToRemove.add(entry.getKey());
            }
        }
        for (String staleSessionId : sessionsToRemove) {
            PermissionService service = INSTANCES_BY_SESSION_ID.remove(staleSessionId);
            if (service != null) {
                service.stop();
            }
        }
    }

    static String newLegacySessionId() {
        return UUID.randomUUID().toString();
    }
}
