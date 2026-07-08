package com.protean.copilot.handler.diff;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 换行符归一化工具。
 * IntelliJ Document 内部使用 LF，diff 比较前统一转换为 LF 可避免因
 * CRLF/LF 差异导致每行都显示为已变更。
 */
public final class LineSeparatorUtil {

    public static final String LF = "\n";
    public static final String CRLF = "\r\n";

    private LineSeparatorUtil() {}

    /** 将所有换行符归一化为 LF (Unix 风格)。 */
    @NotNull
    public static String normalizeToLF(@Nullable String content) {
        if (content == null || content.isEmpty()) {
            return content == null ? "" : content;
        }
        return content.replace("\r\n", "\n").replace("\r", "\n");
    }
}
