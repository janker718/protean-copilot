package com.protean.copilot.bridge;

import com.intellij.openapi.project.Project;
import com.protean.copilot.context.IdeContext;
import com.protean.copilot.context.IdeContextCollector;
import com.protean.copilot.notifications.ProteanNotifier;
import java.util.function.Consumer;

public class LocalToolWindowBridge implements ToolWindowBridge {

    private final Project project;
    private final ToolWindowBridgeView view;

    public LocalToolWindowBridge(Project project, ToolWindowBridgeView view) {
        this.project = project;
        this.view = view;
    }

    @Override
    public void refreshContext() {
        runWithContextStatus("Collecting context...", context -> {
            view.showOutput(renderContextPreview(context));
            view.setStatus("Ready - " + context.targetLabel());
            ProteanNotifier.info(project, "Context refreshed from " + context.targetLabel() + ".");
        });
    }

    @Override
    public void submitPrompt(String prompt) {
        String trimmedPrompt = prompt.trim();
        if (trimmedPrompt.isBlank()) {
            view.setStatus("Waiting for input");
            ProteanNotifier.warning(project, "Prompt is empty.");
            return;
        }

        runWithContextStatus("Collecting context...", context -> {
            view.showOutput(renderMockResponse(trimmedPrompt, context));
            view.clearPrompt();
            view.setStatus("Ready - response prepared");
            ProteanNotifier.info(project, "Prompt prepared with " + context.targetLabel() + ". Agent backend can be connected next.");
        });
    }

    private void runWithContextStatus(String status, Consumer<IdeContext> block) {
        view.setStatus(status);
        try {
            block.accept(IdeContextCollector.collect(project));
        } catch (RuntimeException error) {
            String message = error.getMessage() != null ? error.getMessage() : "unknown error";
            view.setStatus("Error - " + message);
            view.showOutput("Failed to collect IDE context.\n\n" + message);
            ProteanNotifier.error(project, "Failed to collect IDE context: " + message);
        }
    }

    private String renderContextPreview(IdeContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("Context Preview\n");
        sb.append("================\n");
        sb.append(context.summary()).append("\n");
        sb.append("\n");
        int fileTextLen = (context.currentFile() != null && context.currentFile().text() != null)
            ? context.currentFile().text().length() : 0;
        sb.append("Current file text: ").append(fileTextLen).append(" chars\n");
        int selTextLen = (context.selection() != null) ? context.selection().text().length() : 0;
        sb.append("Selection text: ").append(selTextLen).append(" chars");
        return sb.toString();
    }

    private String renderMockResponse(String prompt, IdeContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("Task Status\n");
        sb.append("===========\n");
        sb.append("Status: Agent backend is not connected yet.\n");
        sb.append("Prompt: ").append(prompt).append("\n");
        sb.append("\n");
        sb.append("Collected IDE Context\n");
        sb.append("=====================\n");
        sb.append(context.summary()).append("\n");
        sb.append("\n");
        sb.append("Bridge Boundary\n");
        sb.append("===============\n");
        sb.append("Tool Window UI -> ToolWindowBridge -> ContextCollector / AgentClient\n");
        sb.append("Replace LocalToolWindowBridge with an HTTP/WebView bridge when the Agent runtime is ready.");
        return sb.toString();
    }
}
