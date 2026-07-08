package com.protean.copilot.handler.diff;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 显示交互式差异视图的请求参数。
 */
public class InteractiveDiffRequest {
    private final String filePath;
    private final String originalContent;
    private final String newFileContents;
    private final String tabName;
    private final boolean isNewFile;
    private final boolean readOnly;

    public InteractiveDiffRequest(
            @NotNull String filePath,
            @Nullable String originalContent,
            @NotNull String newFileContents,
            @NotNull String tabName,
            boolean isNewFile,
            boolean readOnly
    ) {
        this.filePath = filePath;
        this.originalContent = originalContent;
        this.newFileContents = newFileContents;
        this.tabName = tabName;
        this.isNewFile = isNewFile;
        this.readOnly = readOnly;
    }

    public static InteractiveDiffRequest forModifiedFile(
            @NotNull String filePath,
            @NotNull String originalContent,
            @NotNull String newFileContents,
            @NotNull String tabName) {
        return new InteractiveDiffRequest(filePath, originalContent, newFileContents, tabName, false, false);
    }

    public static InteractiveDiffRequest forNewFile(
            @NotNull String filePath,
            @NotNull String newFileContents,
            @NotNull String tabName) {
        return new InteractiveDiffRequest(filePath, "", newFileContents, tabName, true, false);
    }

    @NotNull public String getFilePath() { return filePath; }
    @NotNull public String getOriginalContent() { return originalContent != null ? originalContent : ""; }
    @NotNull public String getNewFileContents() { return newFileContents; }
    @NotNull public String getTabName() { return tabName; }
    public boolean isNewFile() { return isNewFile; }
    public boolean isReadOnly() { return readOnly; }
}
