package com.protean.copilot.handler.context;

import java.util.List;
import java.util.Objects;

public record IdeContext(
    String projectName,
    String projectBasePath,
    CurrentFile currentFile,
    Selection selection,
    List<String> openFiles
) {
    public boolean hasSelection() {
        return selection != null;
    }

    public String targetLabel() {
        if (selection != null) {
            String fileName = (currentFile != null) ? currentFile.name() : "current file";
            return "selection " + selection.startLine() + "-" + selection.endLine() + " in " + fileName;
        } else if (currentFile != null) {
            return "file " + currentFile.name();
        } else {
            return "project " + projectName;
        }
    }

    public String summary() {
        String nl = System.lineSeparator();
        StringBuilder sb = new StringBuilder();
        sb.append("Project: ").append(projectName).append(nl);
        sb.append("Base path: ").append(Objects.requireNonNullElse(projectBasePath, "(none)")).append(nl);
        String currentFilePath = (currentFile != null) ? currentFile.path() : "(none)";
        sb.append("Current file: ").append(currentFilePath).append(nl);
        if (selection != null) {
            sb.append("Selection: lines ").append(selection.startLine()).append("-").append(selection.endLine())
              .append(", ").append(selection.text().length()).append(" chars").append(nl);
        } else {
            sb.append("Selection: (none)").append(nl);
        }
        sb.append("Open files: ").append(openFiles.size());
        return sb.toString();
    }
}
