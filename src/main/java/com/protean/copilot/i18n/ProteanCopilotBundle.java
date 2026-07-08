package com.protean.copilot.i18n;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

/**
 * Protean Copilot 插件的国际化资源包。
 *
 * <p>提供对 {@code messages/ProteanCopilotBundle*.properties} 中本地化字符串的访问。
 * 参照 IntelliJ Platform 标准模式实现，继承 {@link DynamicBundle} 以自动解析
 * 当前 IDE 语言环境的正确属性文件。
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * String text = ProteanCopilotBundle.message("action.explain.text");
 * String msg  = ProteanCopilotBundle.message("error.fileNotFound", path);
 * }</pre>
 *
 * @see DynamicBundle
 */
public class ProteanCopilotBundle extends DynamicBundle {

    @NonNls
    private static final String BUNDLE = "messages.ProteanCopilotBundle";

    private static final ProteanCopilotBundle INSTANCE = new ProteanCopilotBundle();

    private ProteanCopilotBundle() {
        super(BUNDLE);
    }

    /**
     * 从资源包中获取本地化消息。
     *
     * @param key    资源键
     * @param params 可选的消息格式化参数（{@code {0}}, {@code {1}}, …）
     * @return 本地化后的消息文本
     */
    @NotNull
    public static @Nls String message(
            @NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
            Object @NotNull ... params) {
        return INSTANCE.getMessage(key, params);
    }
}
