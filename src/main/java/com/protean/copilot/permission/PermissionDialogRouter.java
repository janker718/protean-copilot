package com.protean.copilot.permission;

import com.google.gson.JsonObject;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;

import java.nio.file.Paths;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Owns multi-project dialog registration and cwd-to-project routing.
 */
class PermissionDialogRouter {

    private final Map<Project, PermissionService.PermissionDialogShower> permissionDialogShowers = new ConcurrentHashMap<>();
    private final Map<Project, PermissionService.AskUserQuestionDialogShower> askUserQuestionDialogShowers = new ConcurrentHashMap<>();
    private final Map<Project, PermissionService.PlanApprovalDialogShower> planApprovalDialogShowers = new ConcurrentHashMap<>();
    private final BiConsumer<String, String> debugLog;

    private volatile Project lastActiveProject;

    PermissionDialogRouter(BiConsumer<String, String> debugLog) {
        this.debugLog = debugLog;
    }

    void registerPermissionDialogShower(Project project, PermissionService.PermissionDialogShower shower) {
        if (project == null || shower == null) {
            return;
        }
        permissionDialogShowers.put(project, shower);
        lastActiveProject = project;
    }

    void unregisterPermissionDialogShower(Project project) {
        if (project == null) {
            return;
        }
        permissionDialogShowers.remove(project);
    }

    void registerAskUserQuestionDialogShower(Project project, PermissionService.AskUserQuestionDialogShower shower) {
        if (project == null || shower == null) {
            return;
        }
        askUserQuestionDialogShowers.put(project, shower);
        lastActiveProject = project;
    }

    void unregisterAskUserQuestionDialogShower(Project project) {
        if (project == null) {
            return;
        }
        askUserQuestionDialogShowers.remove(project);
    }

    void registerPlanApprovalDialogShower(Project project, PermissionService.PlanApprovalDialogShower shower) {
        if (project == null || shower == null) {
            return;
        }
        planApprovalDialogShowers.put(project, shower);
        lastActiveProject = project;
    }

    void unregisterPlanApprovalDialogShower(Project project) {
        if (project == null) {
            return;
        }
        planApprovalDialogShowers.remove(project);
    }

    void setLastActiveProject(Project project) {
        if (project != null) {
            lastActiveProject = project;
        }
    }

    PermissionService.PermissionDialogShower findPermissionDialogShower(JsonObject request, String logPrefix) {
        return findDialogShowerByCwd(request, permissionDialogShowers, logPrefix);
    }

    PermissionService.AskUserQuestionDialogShower findAskUserQuestionDialogShower(JsonObject request) {
        return findDialogShowerByCwd(request, askUserQuestionDialogShowers, "MATCH_ASK_PROJECT");
    }

    PermissionService.PlanApprovalDialogShower findPlanApprovalDialogShower(JsonObject request) {
        return findDialogShowerByCwd(request, planApprovalDialogShowers, "MATCH_PLAN_PROJECT");
    }

    Project findProjectByCwd(JsonObject request) {
        PermissionService.PermissionDialogShower shower = findPermissionDialogShower(request, "DIFF_REVIEW_MATCH");
        if (shower == null) {
            return findProjectByPath(request);
        }
        for (Map.Entry<Project, PermissionService.PermissionDialogShower> entry : permissionDialogShowers.entrySet()) {
            if (entry.getValue() == shower) {
                return entry.getKey();
            }
        }
        return findProjectByPath(request);
    }

    Project findProjectByPath(JsonObject request) {
        String cwd = extractCwd(request);
        if (cwd == null || cwd.isEmpty()) {
            return null;
        }

        String normalizedCwd = normalizePathSafely(cwd);
        Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
        Project bestMatch = null;
        int longestMatchLength = 0;

        for (Project project : openProjects) {
            if (project.isDisposed()) {
                continue;
            }
            String basePath = project.getBasePath();
            if (basePath == null) {
                continue;
            }
            String normalizedBasePath = normalizePathSafely(basePath);
            if (pathMatches(normalizedCwd, normalizedBasePath) && normalizedBasePath.length() > longestMatchLength) {
                longestMatchLength = normalizedBasePath.length();
                bestMatch = project;
            }
        }

        return bestMatch;
    }

    int getPermissionDialogCount() {
        return permissionDialogShowers.size();
    }

    private <T> T findDialogShowerByCwd(JsonObject request, Map<Project, T> dialogShowers, String logPrefix) {
        if (dialogShowers.isEmpty()) {
            debugLog.accept(logPrefix, "No dialog showers registered");
            return null;
        }
        if (dialogShowers.size() == 1) {
            return dialogShowers.values().iterator().next();
        }

        String cwd = extractCwd(request);
        if (cwd == null || cwd.isEmpty()) {
            return getPreferredDialogShower(dialogShowers);
        }

        String normalizedCwd = normalizePathSafely(cwd);
        Project bestMatch = null;
        int longestMatchLength = 0;
        for (Map.Entry<Project, T> entry : dialogShowers.entrySet()) {
            Project project = entry.getKey();
            String projectPath = project.getBasePath();
            if (projectPath == null) {
                continue;
            }
            String normalizedProjectPath = normalizePathSafely(projectPath);
            if (pathMatches(normalizedCwd, normalizedProjectPath) && normalizedProjectPath.length() > longestMatchLength) {
                longestMatchLength = normalizedProjectPath.length();
                bestMatch = project;
            }
        }

        if (bestMatch != null) {
            return dialogShowers.get(bestMatch);
        }
        return getPreferredDialogShower(dialogShowers);
    }

    private <T> T getPreferredDialogShower(Map<Project, T> showers) {
        if (showers.isEmpty()) {
            return null;
        }
        if (lastActiveProject != null && showers.containsKey(lastActiveProject)) {
            return showers.get(lastActiveProject);
        }
        return showers.values().iterator().next();
    }

    private String extractCwd(JsonObject request) {
        if (request == null || !request.has("cwd") || request.get("cwd").isJsonNull()) {
            return null;
        }
        return request.get("cwd").getAsString();
    }

    private String normalizePathSafely(String path) {
        if (path == null) {
            return null;
        }
        try {
            return Paths.get(path).toAbsolutePath().normalize().toString().replace('\\', '/');
        } catch (Exception e) {
            return path.replace('\\', '/');
        }
    }

    private boolean pathMatches(String cwdPath, String projectBasePath) {
        if (cwdPath == null || projectBasePath == null) {
            return false;
        }
        boolean isWindows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String cwd = isWindows ? cwdPath.toLowerCase(Locale.ROOT) : cwdPath;
        String base = isWindows ? projectBasePath.toLowerCase(Locale.ROOT) : projectBasePath;
        if (cwd.equals(base)) {
            return true;
        }
        String baseWithSep = base.endsWith("/") ? base : base + "/";
        return cwd.startsWith(baseWithSep);
    }
}
