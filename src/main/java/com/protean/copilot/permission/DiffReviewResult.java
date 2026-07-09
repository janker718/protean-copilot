package com.protean.copilot.permission;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Result of a diff review operation initiated by the permission system.
 */
public class DiffReviewResult {

    private final boolean accepted;
    private final boolean alwaysAllow;
    private final String finalContent;
    private final String filePath;

    private DiffReviewResult(boolean accepted, boolean alwaysAllow, @Nullable String finalContent, @NotNull String filePath) {
        this.accepted = accepted;
        this.alwaysAllow = alwaysAllow;
        this.finalContent = finalContent;
        this.filePath = filePath;
    }

    public static DiffReviewResult accepted(@NotNull String finalContent, @NotNull String filePath) {
        return new DiffReviewResult(true, false, finalContent, filePath);
    }

    public static DiffReviewResult acceptedAlways(@NotNull String finalContent, @NotNull String filePath) {
        return new DiffReviewResult(true, true, finalContent, filePath);
    }

    public static DiffReviewResult rejected(@NotNull String filePath) {
        return new DiffReviewResult(false, false, null, filePath);
    }

    public boolean isAccepted() {
        return accepted;
    }

    public boolean isAlwaysAllow() {
        return alwaysAllow;
    }

    public boolean isAllowed() {
        return accepted;
    }

    @Nullable
    public String getFinalContent() {
        return finalContent;
    }

    @NotNull
    public String getFilePath() {
        return filePath;
    }
}
