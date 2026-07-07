package com.protean.copilot.ui;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
import com.protean.copilot.bridge.LocalToolWindowBridge;

public class ProteanToolWindow implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        ProteanToolWindowPanel panel = new ProteanToolWindowPanel(project);
        panel.bindBridge(new LocalToolWindowBridge(project, panel));
        var content = ContentFactory.getInstance().createContent(panel, "", false);
        toolWindow.getContentManager().addContent(content);
    }
}
