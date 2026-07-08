package com.protean.copilot.handler.diff;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Diff 处理器的共享文件系统操作，含路径遍历安全检查。
 */
public class DiffFileOperations {

    private static final Logger LOG = Logger.getInstance(DiffFileOperations.class);
    private final Project project;

    public DiffFileOperations(Project project) {
        this.project = project;
    }

    /** 安全检查：文件路径是否在项目目录内。 */
    public boolean isPathWithinProject(String filePath) {
        String basePath = project.getBasePath();
        if (basePath == null) {
            LOG.warn("Security: Cannot validate path — project base path is null");
            return false;
        }
        try {
            File file = new File(filePath).getCanonicalFile();
            File base = new File(basePath).getCanonicalFile();
            return file.toPath().startsWith(base.toPath());
        } catch (IOException e) {
            LOG.warn("Security: Path validation failed: " + e.getMessage());
            return false;
        }
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
                        .refreshAndFindFileByPath(filePath);
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
                    LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath);
                    LOG.info("New file created: " + filePath);
                }
            } catch (Exception e) {
                LOG.error("Failed to write content: " + filePath, e);
            }
        });
    }
}
