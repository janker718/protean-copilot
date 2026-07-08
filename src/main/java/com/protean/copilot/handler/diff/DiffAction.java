package com.protean.copilot.handler.diff;

/**
 * 用户对交互式差异视图的操作。
 */
public enum DiffAction {
    /** 用户接受更改 */
    APPLY,
    /** 用户接受更改并选择"始终允许"此工具类型 */
    APPLY_ALWAYS,
    /** 用户拒绝更改（点击了 Reject 按钮） */
    REJECT,
    /** 用户关闭了差异窗口，未做任何操作 */
    DISMISS
}
