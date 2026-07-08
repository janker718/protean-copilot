package com.protean.copilot.diff;

import org.jetbrains.annotations.Nullable;

/**
 * 交互式差异操作的结果。
 */
public class DiffResult {
    private final DiffAction action;
    private final String finalContent;

    public DiffResult(DiffAction action, @Nullable String finalContent) {
        this.action = action;
        this.finalContent = finalContent;
    }

    public static DiffResult apply(String finalContent) {
        return new DiffResult(DiffAction.APPLY, finalContent);
    }

    public static DiffResult applyAlways(String finalContent) {
        return new DiffResult(DiffAction.APPLY_ALWAYS, finalContent);
    }

    public static DiffResult reject() {
        return new DiffResult(DiffAction.REJECT, null);
    }

    public static DiffResult dismiss() {
        return new DiffResult(DiffAction.DISMISS, null);
    }

    public DiffAction getAction() { return action; }

    @Nullable
    public String getFinalContent() { return finalContent; }

    public boolean isApplied() {
        return action == DiffAction.APPLY || action == DiffAction.APPLY_ALWAYS;
    }

    public boolean isAppliedAlways() { return action == DiffAction.APPLY_ALWAYS; }
    public boolean isRejected() { return action == DiffAction.REJECT; }
    public boolean isDismissed() { return action == DiffAction.DISMISS; }
}
