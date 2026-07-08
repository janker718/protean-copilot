package com.protean.copilot.statusbar;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBarWidget;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.util.Consumer;
import com.protean.copilot.settings.manager.ProviderManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.MouseEvent;

public final class ProteanStatusBarWidget implements StatusBarWidget, StatusBarWidget.TextPresentation {

    private static final String ID = "ProteanCopilot.StatusBar";
    private static final String TOOL_WINDOW_ID = "Protean Copilot";

    private final Project project;

    public ProteanStatusBarWidget(Project project) {
        this.project = project;
    }

    @Override
    public @NotNull String ID() {
        return ID;
    }

    @Override
    public @Nullable WidgetPresentation getPresentation() {
        return this;
    }

    @Override
    public float getAlignment() {
        return 0.5f;
    }

    @Override
    public @NotNull String getText() {
        return "Protean:" + ProviderManager.getInstance(project).getActiveProvider();
    }

    @Override
    public @Nullable String getTooltipText() {
        return "Open Protean Copilot tool window";
    }

    @Override
    public @Nullable Consumer<MouseEvent> getClickConsumer() {
        return event -> {
            ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID);
            if (toolWindow != null) {
                toolWindow.activate(null, true, true);
            }
        };
    }
}
