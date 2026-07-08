package com.protean.copilot.settings.manager;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.protean.copilot.settings.SettingsService;
import org.jetbrains.annotations.NotNull;

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
        String projectPath = project.getBasePath();
        if (projectPath == null || projectPath.isBlank()) {
            return ".";
        }
        String custom = settingsService.getCustomWorkingDirectory(projectPath);
        return custom == null || custom.isBlank() ? projectPath : custom;
    }

    public void setCustomWorkingDirectory(@NotNull String workingDirectory) {
        String projectPath = project.getBasePath();
        if (projectPath != null && !projectPath.isBlank()) {
            settingsService.setCustomWorkingDirectory(projectPath, workingDirectory);
        }
    }
}
