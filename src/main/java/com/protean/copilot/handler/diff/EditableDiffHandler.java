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

public final class EditableDiffHandler implements DiffActionHandler {

    private static final Logger LOG = Logger.getInstance(EditableDiffHandler.class);
    private static final String[] TYPES = {"show_editable_diff"};

    private final HandlerContext context;
    private final Gson gson;
    private final DiffBrowserBridge browserBridge;
    private final DiffFileOperations fileOperations;

    public EditableDiffHandler(
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
            String status = json.has("status") ? json.get("status").getAsString() : "M";
            boolean isNewFile = "A".equals(status);
            String currentContent = gson.toJson(json.has("operations") ? json.get("operations") : json);

            if (!fileOperations.isPathWithinProject(filePath)) {
                browserBridge.sendDiffResult(filePath, "REJECT", null, "File path outside project");
                return;
            }

            String tabName = (isNewFile ? "New: " : "Edit: ") + new File(filePath).getName();
            ApplicationManager.getApplication().invokeLater(() ->
                showEditableDiff(filePath, currentContent, tabName, isNewFile));
        } catch (Exception e) {
            LOG.error("Failed to parse show_editable_diff request", e);
        }
    }

    private void showEditableDiff(String filePath, String newContent, String tabName, boolean isNewFile) {
        try {
            String original = "";
            if (!isNewFile) {
                VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(WslPathUtil.toVfsPath(filePath));
                if (vFile != null) {
                    vFile.refresh(false, false);
                    Charset charset = vFile.getCharset() != null ? vFile.getCharset() : StandardCharsets.UTF_8;
                    try {
                        original = new String(vFile.contentsToByteArray(), charset);
                    } catch (IOException e) {
                        browserBridge.sendDiffResult(filePath, "REJECT", null, "Failed to read file");
                        return;
                    }
                }
            }

            InteractiveDiffRequest request = isNewFile
                ? InteractiveDiffRequest.forNewFile(filePath, newContent, tabName)
                : InteractiveDiffRequest.forModifiedFile(filePath, original, newContent, tabName);

            String toolName = isNewFile ? "Write" : "Edit";
            String originalContent = original;
            InteractiveDiffManager.showInteractiveDiff(context.getProject(), request)
                .thenAccept(result -> handleDiffResult(result, toolName, filePath, originalContent, isNewFile))
                .exceptionally(e -> {
                    LOG.error("Error in editable diff: " + e.getMessage(), e);
                    browserBridge.sendDiffResult(filePath, "REJECT", null, e.getMessage());
                    return null;
                });
        } catch (Exception e) {
            LOG.error("Failed to show editable diff", e);
            browserBridge.sendDiffResult(filePath, "REJECT", null, e.getMessage());
        }
    }

    private void handleDiffResult(DiffResult result, String toolName, String filePath, String originalContent, boolean isNewFile) {
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
            if (isNewFile) {
                fileOperations.deleteFile(filePath);
            } else {
                fileOperations.writeContentToFile(filePath, originalContent);
            }
            browserBridge.sendDiffResult(filePath, "REJECT", null, null);
            return;
        }

        browserBridge.sendDiffResult(filePath, "DISMISS", null, null);
    }
}
