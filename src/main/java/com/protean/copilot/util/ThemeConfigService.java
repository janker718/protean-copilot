package com.protean.copilot.util;

import com.intellij.ide.ui.LafManager;

/**
 * IDE 主题配置服务。
 * 检测当前 IDE 主题是否为深色主题，并提供背景颜色值。
 */
public final class ThemeConfigService {

    private ThemeConfigService() {
        // 工具类，禁止实例化
    }

    /**
     * 检查当前 IDE 主题是否为深色主题。
     */
    public static boolean isDarkTheme() {
        try {
            var laf = LafManager.getInstance().getCurrentLookAndFeel();
            var name = laf.getName().toLowerCase();
            return name.contains("dark") || name.contains("darcula");
        } catch (Exception ignored) {
            return true; // 默认为深色主题
        }
    }

    /**
     * 获取背景颜色的十六进制字符串。
     */
    public static String getBackgroundColorHex() {
        return isDarkTheme() ? "#1e1e1e" : "#ffffff";
    }
}
