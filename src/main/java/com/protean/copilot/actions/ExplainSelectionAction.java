package com.protean.copilot.actions;

import com.protean.copilot.context.IdeContextCollector;
import com.protean.copilot.notifications.ProteanNotifier;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;

public class ExplainSelectionAction extends AnAction implements DumbAware {
    @Override
    public void actionPerformed(AnActionEvent event) {
        var project = event.getProject();
        if (project == null) return;
        try {
            var context = IdeContextCollector.collect(event);
            ProteanNotifier.info(
                project,
                "Collected " + context.targetLabel() + ". " + context.openFiles().size() + " open file(s) are available as context."
            );
        } catch (RuntimeException error) {
            String msg = error.getMessage() != null ? error.getMessage() : "unknown error";
            ProteanNotifier.error(project, "Failed to collect IDE context: " + msg);
        }
    }

    @Override
    public void update(AnActionEvent event) {
        event.getPresentation().setEnabled(event.getProject() != null);
    }

    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
