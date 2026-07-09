package com.protean.copilot.settings.manager;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.protean.copilot.settings.SettingsService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.PROJECT)
public final class WorkingDirectoryManager {

    public static WorkingDirectoryManager getInstance(@NotNull Project project) {
        return project.getService(WorkingDirectoryManager.class);
    }

    private final Project project;
    private final SettingsService settingsService = new SettingsService();

    public WorkingDirectoryManager(Project project) {
        this.project = project;
    }

    public @NotNull String resolveWorkingDirectory() {
        return resolveWorkingDirectory(null);
    }

    public @NotNull String resolveWorkingDirectory(@Nullable String workingDirectory) {
        String normalizedInput = normalize(workingDirectory);
        if (normalizedInput != null) {
            return normalizedInput;
        }

        String projectPath = project.getBasePath();
        String custom = projectPath == null || projectPath.isBlank()
            ? null
            : normalize(settingsService.getCustomWorkingDirectory(projectPath));

        if (custom != null) {
            return custom;
        }
        if (projectPath != null && !projectPath.isBlank()) {
            return projectPath;
        }
        return System.getProperty("user.home", ".");
    }

    public void setCustomWorkingDirectory(@NotNull String workingDirectory) {
        String projectPath = project.getBasePath();
        if (projectPath != null && !projectPath.isBlank()) {
            settingsService.setCustomWorkingDirectory(projectPath, resolveWorkingDirectory(workingDirectory));
        }
    }

    private static @Nullable String normalize(@Nullable String workingDirectory) {
        if (workingDirectory == null) {
            return null;
        }
        String trimmed = workingDirectory.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
