package com.protean.copilot.handler.diff;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.jcef.JBCefBrowser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 处理交互式差异显示请求（show_interactive_diff / show_editable_diff）。
 * 打开带 Apply/Reject 按钮的 IntelliJ 差异视图，用户操作后回传前端。
 */
public class InteractiveDiffHandler {

    private static final Logger LOG = Logger.getInstance(InteractiveDiffHandler.class);

    private final Project project;
    private final Gson gson;
    private final DiffBrowserBridge bridge;
    private final DiffFileOperations fileOps;

    public InteractiveDiffHandler(Project project, Gson gson, DiffBrowserBridge bridge, DiffFileOperations fileOps) {
        this.project = project;
        this.gson = gson;
        this.bridge = bridge;
        this.fileOps = fileOps;
    }

    public String[] getSupportedTypes() {
        return new String[]{"show_interactive_diff", "show_editable_diff"};
    }

    public boolean handle(String type, String content) {
        return switch (type) {
            case "show_interactive_diff" -> { handleInteractive(content); yield true; }
            case "show_editable_diff" -> { handleEditable(content); yield true; }
            default -> false;
        };
    }

    private void handleInteractive(String content) {
        try {
            JsonObject json = gson.fromJson(content, JsonObject.class);
            String filePath = getString(json, "filePath");
            String newContent = getString(json, "newFileContents");
            String tabName = json.has("tabName") ? json.get("tabName").getAsString() : null;
            boolean isNewFile = json.has("isNewFile") && json.get("isNewFile").getAsBoolean();

            if (!fileOps.isPathWithinProject(filePath)) {
                bridge.sendDiffResult(filePath, "REJECT", null, "Path outside project"); return;
            }
            String finalTabName = (tabName == null || tabName.isEmpty())
                ? "Edit: " + new File(filePath).getName() : tabName;

            ApplicationManager.getApplication().invokeLater(() -> doShow(filePath, newContent, finalTabName, isNewFile));
        } catch (Exception e) { LOG.error("show_interactive_diff failed", e); }
    }

    private void handleEditable(String content) {
        try {
            JsonObject json = gson.fromJson(content, JsonObject.class);
            String filePath = getString(json, "filePath");
            String status = getString(json, "status"); // "A" or "M"
            boolean isNewFile = "A".equals(status);
            String newContent = gson.toJson(json.has("operations") ? json.get("operations") : json);

            if (!fileOps.isPathWithinProject(filePath)) {
                bridge.sendDiffResult(filePath, "REJECT", null, "Path outside project"); return;
            }

            String tabName = (isNewFile ? "New: " : "Edit: ") + new File(filePath).getName();
            ApplicationManager.getApplication().invokeLater(() -> doShow(filePath, newContent, tabName, isNewFile));
        } catch (Exception e) { LOG.error("show_editable_diff failed", e); }
    }

    private void doShow(String filePath, String newContent, String tabName, boolean isNewFile) {
        try {
            String original = "";
            if (!isNewFile) {
                VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath);
                if (vf != null) {
                    vf.refresh(false, false);
                    Charset cs = vf.getCharset() != null ? vf.getCharset() : StandardCharsets.UTF_8;
                    try { original = new String(vf.contentsToByteArray(), cs); }
                    catch (IOException e) { bridge.sendDiffResult(filePath, "REJECT", null, "Read failed"); return; }
                }
            }

            InteractiveDiffRequest req = isNewFile
                ? InteractiveDiffRequest.forNewFile(filePath, newContent, tabName)
                : InteractiveDiffRequest.forModifiedFile(filePath, original, newContent, tabName);

            InteractiveDiffManager.showInteractiveDiff(project, req)
                .thenAccept(result -> {
                    if (result.isApplied()) {
                        fileOps.writeContentToFile(filePath, result.getFinalContent());
                        bridge.sendDiffResult(filePath, "APPLY", result.getFinalContent(), null);
                    } else if (result.isRejected()) {
                        bridge.sendDiffResult(filePath, "REJECT", null, null);
                    } else {
                        LOG.info("Diff dismissed: " + filePath);
                        bridge.sendDiffResult(filePath, "DISMISS", null, null);
                    }
                }).exceptionally(e -> {
                    LOG.error("Interactive diff error: " + e.getMessage(), e);
                    bridge.sendDiffResult(filePath, "REJECT", null, e.getMessage());
                    return null;
                });
        } catch (Exception e) {
            LOG.error("Failed to show interactive diff: " + e.getMessage(), e);
        }
    }

    private String getString(JsonObject obj, String key) {
        return obj.has(key) ? obj.get(key).getAsString() : "";
    }
}
