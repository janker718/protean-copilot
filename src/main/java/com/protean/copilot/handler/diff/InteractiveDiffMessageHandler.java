package com.protean.copilot.handler.diff;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.util.WslPathUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class InteractiveDiffMessageHandler implements DiffActionHandler {

    private static final Logger LOG = Logger.getInstance(InteractiveDiffMessageHandler.class);
    private static final String[] TYPES = {"show_interactive_diff"};

    private final HandlerContext context;
    private final Gson gson;
    private final DiffBrowserBridge browserBridge;
    private final DiffFileOperations fileOperations;

    public InteractiveDiffMessageHandler(
        HandlerContext context,
        Gson gson,
        DiffBrowserBridge browserBridge,
        DiffFileOperations fileOperations
    ) {
        this.context = context;
        this.gson = gson;
        this.browserBridge = browserBridge;
        this.fileOperations = fileOperations;
    }

    @Override
    public String[] getSupportedTypes() {
        return TYPES;
    }

    @Override
    public void handle(String type, String content) {
        try {
            JsonObject json = gson.fromJson(content, JsonObject.class);
            String filePath = json.has("filePath") ? json.get("filePath").getAsString() : "";
            String newFileContents = json.has("newFileContents") ? json.get("newFileContents").getAsString() : "";
            String tabName = json.has("tabName") ? json.get("tabName").getAsString() : null;
            boolean isNewFile = json.has("isNewFile") && json.get("isNewFile").getAsBoolean();

            if (!fileOperations.isPathWithinProject(filePath)) {
                browserBridge.sendDiffResult(filePath, "REJECT", null, "File path outside project");
                return;
            }
            if (filePath.isEmpty()) {
                browserBridge.sendDiffResult(filePath, "REJECT", null, "File path is empty");
                return;
            }

            String resolvedTabName = (tabName == null || tabName.isEmpty())
                ? "Edit: " + new File(filePath).getName()
                : tabName;

            ApplicationManager.getApplication().invokeLater(() ->
                showInteractiveDiff(filePath, newFileContents, resolvedTabName, isNewFile));
        } catch (Exception e) {
            LOG.error("Failed to parse show_interactive_diff request", e);
        }
    }

    private void showInteractiveDiff(String filePath, String newFileContents, String tabName, boolean isNewFile) {
        try {
            String originalContent = "";
            if (!isNewFile) {
                VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(WslPathUtil.toVfsPath(filePath));
                if (vFile != null) {
                    vFile.refresh(false, false);
                    Charset charset = vFile.getCharset() != null ? vFile.getCharset() : StandardCharsets.UTF_8;
                    try {
                        originalContent = new String(vFile.contentsToByteArray(), charset);
                    } catch (IOException e) {
                        browserBridge.sendDiffResult(filePath, "REJECT", null, "Failed to read file");
                        return;
                    }
                }
            }

            InteractiveDiffRequest request = isNewFile
                ? InteractiveDiffRequest.forNewFile(filePath, newFileContents, tabName)
                : InteractiveDiffRequest.forModifiedFile(filePath, originalContent, newFileContents, tabName);

            String toolName = isNewFile ? "Write" : "Edit";
            InteractiveDiffManager.showInteractiveDiff(context.getProject(), request)
                .thenAccept(result -> handleDiffResult(result, toolName, filePath))
                .exceptionally(e -> {
                    LOG.error("Error in interactive diff: " + e.getMessage(), e);
                    browserBridge.sendDiffResult(filePath, "REJECT", null, e.getMessage());
                    return null;
                });
        } catch (Exception e) {
            LOG.error("Failed to show interactive diff: " + e.getMessage(), e);
            browserBridge.sendDiffResult(filePath, "REJECT", null, e.getMessage());
        }
    }

    private void handleDiffResult(DiffResult result, String toolName, String filePath) {
        if (result.isApplied()) {
            fileOperations.applyDiffChangeWithPermission(toolName, filePath, result.getFinalContent())
                .thenAccept(allowed -> browserBridge.sendDiffResult(
                    filePath,
                    allowed ? "APPLY" : "REJECT",
                    allowed ? result.getFinalContent() : null,
                    allowed ? null : "Permission denied"
                ));
            return;
        }
        if (result.isRejected()) {
            browserBridge.sendDiffResult(filePath, "REJECT", null, null);
            return;
        }
        browserBridge.sendDiffResult(filePath, "DISMISS", null, null);
    }
}
