package com.protean.copilot.ui;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.ContentFactory;
import com.protean.copilot.ui.toolwindow.ProteanChatWindow;

public class ProteanToolWindow implements ToolWindowFactory, DumbAware {

    @Override
    public void createToolWindowContent(Project project, ToolWindow toolWindow) {
        ProteanChatWindow chatWindow = new ProteanChatWindow(project);
        var content = ContentFactory.getInstance().createContent(
            chatWindow.getContent(), "", false);
        chatWindow.setParentContent(content);
        content.setDisposer(() -> chatWindow.dispose());
        toolWindow.getContentManager().addContent(content);
    }
}
