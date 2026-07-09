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
        return exportAsMarkdown(session.ensureSessionId(), session.getMessages());
    }

    public @NotNull String exportAsMarkdown(@NotNull String sessionId, @NotNull java.util.List<ChatSession.Message> messages) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Session ").append(sessionId).append("\n\n");
        for (ChatSession.Message message : messages) {
            builder.append("## ").append(message.type.name()).append("\n\n");
            builder.append(message.content == null ? "" : message.content).append("\n\n");
        }
        return builder.toString();
    }
}
