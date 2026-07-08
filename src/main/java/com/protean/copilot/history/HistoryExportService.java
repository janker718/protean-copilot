package com.protean.copilot.history;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.protean.copilot.session.ChatSession;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.PROJECT)
public final class HistoryExportService {

    public static HistoryExportService getInstance(@NotNull Project project) {
        return project.getService(HistoryExportService.class);
    }

    public @NotNull String exportAsMarkdown(@NotNull ChatSession session) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Session ").append(session.ensureSessionId()).append("\n\n");
        for (ChatSession.Message message : session.getMessages()) {
            builder.append("## ").append(message.type.name()).append("\n\n");
            builder.append(message.content == null ? "" : message.content).append("\n\n");
        }
        return builder.toString();
    }
}
