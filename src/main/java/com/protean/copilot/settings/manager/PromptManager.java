package com.protean.copilot.settings.manager;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.PROJECT)
public final class PromptManager {

    public static PromptManager getInstance(@NotNull Project project) {
        return project.getService(PromptManager.class);
    }

    private volatile String projectPrompt;

    public @Nullable String getProjectPrompt() {
        return projectPrompt;
    }

    public void setProjectPrompt(@Nullable String projectPrompt) {
        this.projectPrompt = projectPrompt;
    }
}
