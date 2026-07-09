package com.protean.copilot.handler.diff;

import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.permission.PermissionRequest;
import com.protean.copilot.permission.PermissionService;
import com.protean.copilot.util.WslPathUtil;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

/**
 * Diff 处理器的共享文件系统操作，含路径遍历安全检查。
 */
public class DiffFileOperations {

    private static final Logger LOG = Logger.getInstance(DiffFileOperations.class);
    private final HandlerContext context;

    public DiffFileOperations(HandlerContext context) {
        this.context = context;
    }

    /** 安全检查：文件路径是否在项目目录内。 */
    public boolean isPathWithinProject(String filePath) {
        String basePath = context.getProject().getBasePath();
        if (basePath == null) {
            LOG.warn("Security: Cannot validate path — project base path is null");
            return false;
        }
        return WslPathUtil.isPathWithinDirectory(filePath, basePath);
    }

    public void deleteFile(String filePath) {
        if (!isPathWithinProject(filePath)) {
            LOG.warn("Security: Attempted to delete file outside project directory: " + filePath);
            return;
        }

        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                VirtualFile file = LocalFileSystem.getInstance()
                    .refreshAndFindFileByPath(WslPathUtil.toVfsPath(filePath));
                if (file != null) {
                    ApplicationManager.getApplication().runWriteAction(() -> {
                        try {
                            file.delete(this);
                            LOG.info("File deleted: " + filePath);
                        } catch (IOException e) {
                            LOG.error("Failed to delete file: " + filePath, e);
                        }
                    });
                } else {
                    File javaFile = new File(filePath);
                    if (javaFile.exists() && !javaFile.delete()) {
                        LOG.warn("Failed to delete file via fallback: " + filePath);
                    }
                }
            } catch (Exception e) {
                LOG.error("Failed to delete file: " + filePath, e);
            }
        });
    }

    public CompletableFuture<Boolean> applyDiffChangeWithPermission(String toolName, String filePath, String content) {
        if (content == null) {
            return CompletableFuture.completedFuture(false);
        }
        if (!isPathWithinProject(filePath)) {
            LOG.warn("Security: Attempted to apply diff outside project directory: " + filePath);
            return CompletableFuture.completedFuture(false);
        }

        JsonObject inputs = new JsonObject();
        inputs.addProperty("file_path", filePath);
        inputs.addProperty("content", content);

        PermissionService permissionService = context.getPermissionService();
        CompletableFuture<PermissionRequest.PermissionResult> permissionFuture = permissionService != null
            ? permissionService.requestLocalPermission(toolName, inputs, null, context.getProject())
            : CompletableFuture.completedFuture(new PermissionRequest.PermissionResult(
                PermissionRequest.PermissionResult.Behavior.ALLOW,
                null,
                null,
                null,
                false
            ));

        return permissionFuture.thenApply(result -> {
            boolean allowed = result != null && result.getBehavior() == PermissionRequest.PermissionResult.Behavior.ALLOW;
            if (allowed) {
                writeContentToFile(filePath, content);
            }
            return allowed;
        });
    }

    /** 将内容写入文件并刷新 VFS。 */
    public void writeContentToFile(String filePath, String content) {
        if (content == null) return;
        if (!isPathWithinProject(filePath)) {
            LOG.warn("Security: write outside project: " + filePath);
            return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                VirtualFile file = LocalFileSystem.getInstance()
                        .refreshAndFindFileByPath(WslPathUtil.toVfsPath(filePath));
                if (file != null) {
                    Charset charset = file.getCharset() != null ? file.getCharset() : StandardCharsets.UTF_8;
                    ApplicationManager.getApplication().runWriteAction(() -> {
                        try {
                            file.setBinaryContent(content.getBytes(charset));
                            file.refresh(false, false);
                            LOG.info("File written: " + filePath);
                        } catch (IOException e) {
                            LOG.error("Failed to write file: " + filePath, e);
                        }
                    });
                } else {
                    File javaFile = new File(filePath);
                    File parent = javaFile.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    Files.write(javaFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
                    LocalFileSystem.getInstance().refreshAndFindFileByPath(WslPathUtil.toVfsPath(filePath));
                    LOG.info("New file created: " + filePath);
                }
            } catch (Exception e) {
                LOG.error("Failed to write content: " + filePath, e);
            }
        });
    }
}
