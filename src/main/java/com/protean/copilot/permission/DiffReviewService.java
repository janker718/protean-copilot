package com.protean.copilot.permission;

import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.protean.copilot.handler.diff.DiffResult;
import com.protean.copilot.handler.diff.InteractiveDiffManager;
import com.protean.copilot.handler.diff.InteractiveDiffRequest;
import com.protean.copilot.util.WslPathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * Service for reviewing file-modifying tool calls via an interactive diff view.
 */
public class DiffReviewService {

    private static final Logger LOG = Logger.getInstance(DiffReviewService.class);
    public static boolean isFileModifyingTool(@Nullable String toolName) {
        return PermissionToolCatalog.supportsDiffReview(toolName);
    }

    @Nullable
    public static CompletableFuture<DiffReviewResult> reviewFileChange(
        @NotNull Project project,
        @NotNull String toolName,
        @NotNull JsonObject inputs
    ) {
        String filePath = extractFilePath(inputs);
        if (filePath == null || filePath.isEmpty()) {
            return null;
        }

        String projectBasePath = project.getBasePath();
        if (projectBasePath != null && !WslPathUtil.isPathWithinDirectory(filePath, projectBasePath)) {
            return null;
        }

        try {
            String originalContent = readFileContent(filePath);
            String proposedContent = computeProposedContent(toolName, inputs, originalContent, filePath);
            if (proposedContent == null) {
                return null;
            }

            boolean isNewFile = originalContent == null;
            String safeOriginal = originalContent != null ? originalContent : "";
            String fileName = new File(filePath).getName();
            String tabName = "Review changes: " + fileName;

            InteractiveDiffRequest request = isNewFile
                ? InteractiveDiffRequest.forReadOnlyNewFile(filePath, proposedContent, tabName)
                : InteractiveDiffRequest.forReadOnlyModifiedFile(filePath, safeOriginal, proposedContent, tabName);

            CompletableFuture<DiffResult> diffFuture = InteractiveDiffManager.showInteractiveDiff(project, request);
            return diffFuture.thenApply(diffResult -> {
                if (diffResult.isApplied()) {
                    return diffResult.isAppliedAlways()
                        ? DiffReviewResult.acceptedAlways(diffResult.getFinalContent(), filePath)
                        : DiffReviewResult.accepted(diffResult.getFinalContent(), filePath);
                }
                return DiffReviewResult.rejected(filePath);
            });
        } catch (Exception e) {
            LOG.error("DiffReview setup failed for " + filePath, e);
            return null;
        }
    }

    @Nullable
    private static String extractFilePath(@NotNull JsonObject inputs) {
        if (inputs.has("file_path") && !inputs.get("file_path").isJsonNull()) {
            return inputs.get("file_path").getAsString();
        }
        if (inputs.has("notebook_path") && !inputs.get("notebook_path").isJsonNull()) {
            return inputs.get("notebook_path").getAsString();
        }
        if (inputs.has("path") && !inputs.get("path").isJsonNull()) {
            return inputs.get("path").getAsString();
        }
        return null;
    }

    @Nullable
    private static String readFileContent(@NotNull String filePath) {
        return ApplicationManager.getApplication().runReadAction((Computable<String>) () -> {
            try {
                VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath.replace('\\', '/'));
                if (vFile != null && vFile.exists() && !vFile.isDirectory()) {
                    Charset charset = vFile.getCharset() != null ? vFile.getCharset() : StandardCharsets.UTF_8;
                    return new String(vFile.contentsToByteArray(), charset);
                }
            } catch (IOException e) {
                LOG.warn("Failed to read file: " + filePath, e);
            }
            return null;
        });
    }

    @Nullable
    private static String computeProposedContent(
        @NotNull String toolName,
        @NotNull JsonObject inputs,
        @Nullable String originalContent,
        @NotNull String filePath
    ) {
        return switch (toolName) {
            case "Edit" -> computeEditProposedContent(inputs, originalContent, filePath);
            case "Write" -> computeWriteProposedContent(inputs);
            default -> null;
        };
    }

    @Nullable
    private static String computeEditProposedContent(
        @NotNull JsonObject inputs,
        @Nullable String originalContent,
        @NotNull String filePath
    ) {
        if (originalContent == null) {
            return null;
        }

        String oldString = inputs.has("old_string") && !inputs.get("old_string").isJsonNull()
            ? inputs.get("old_string").getAsString() : null;
        String newString = inputs.has("new_string") && !inputs.get("new_string").isJsonNull()
            ? inputs.get("new_string").getAsString() : null;
        if (oldString == null || newString == null) {
            LOG.warn("Edit tool missing old_string or new_string for " + filePath);
            return null;
        }
        if (oldString.equals(newString)) {
            return originalContent;
        }

        boolean replaceAll = inputs.has("replace_all") && inputs.get("replace_all").getAsBoolean();
        if (!replaceAll) {
            int index = originalContent.indexOf(oldString);
            if (index >= 0) {
                return originalContent.substring(0, index)
                    + newString
                    + originalContent.substring(index + oldString.length());
            }
            return null;
        }

        String result = originalContent.replace(oldString, newString);
        return !result.equals(originalContent) ? result : null;
    }

    @Nullable
    private static String computeWriteProposedContent(@NotNull JsonObject inputs) {
        if (inputs.has("content") && !inputs.get("content").isJsonNull()) {
            return inputs.get("content").getAsString();
        }
        return null;
    }
}
