package com.protean.copilot.util;

import java.util.regex.Pattern;

/**
 * 用于安全 JS 代码生成与转义的 JavaScript 工具函数。
 * 从 Claude Code GUI 参考实现移植。
 */
public final class JsUtils {

    private JsUtils() {
        // 工具类，禁止实例化
    }

    /** 安全 JavaScript 函数名称的正则表达式。 */
    public static final Pattern SAFE_JS_NAME = Pattern.compile("^[a-zA-Z_$][a-zA-Z0-9_$.]*$");

    /**
     * 对字符串进行转义，以便安全嵌入 JavaScript 字符串字面量。
     * 转义反斜杠、双引号、换行符、回车符、制表符，
     * 以及 Unicode 行分隔符和段分隔符。
     */
    public static String escapeJs(String str) {
        return str
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                .replace(" ", "\\u2028")
                .replace(" ", "\\u2029");
    }

    /**
     * 构建 JavaScript 函数调用字符串。
     * 如果函数名不包含点号，则自动添加 `window.` 前缀。
     */
    public static String buildJsCall(String functionName, String... args) {
        String fn = functionName.contains(".") ? functionName : "window." + functionName;
        StringBuilder argsStr = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                argsStr.append(",");
            }
            argsStr.append("\"").append(args[i]).append("\"");
        }
        return fn + "(" + argsStr.toString() + ")";
    }

    /**
     * 构建包装在 try/catch 中的安全 JavaScript 调用。
     * 使用 {@link #buildJsCall} 构造调用并以错误处理包裹。
     */
    public static String buildSafeJsCall(String functionName, String... args) {
        String[] escaped = new String[args.length];
        for (int i = 0; i < args.length; i++) {
            escaped[i] = escapeJs(args[i]);
        }
        String call = buildJsCall(functionName, escaped);
        return "try { " + call + " } catch(e) { console.error('" + escapeJs(functionName) + " call failed:', e); }";
    }
}
