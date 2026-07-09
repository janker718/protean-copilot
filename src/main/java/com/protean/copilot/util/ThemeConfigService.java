package com.protean.copilot.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.JBColor;

import java.awt.Color;

public final class ThemeConfigService {

    private static final Logger LOG = Logger.getInstance(ThemeConfigService.class);
    public static final Color DARK_BG_COLOR = new Color(30, 30, 30);
    public static final Color LIGHT_BG_COLOR = Color.WHITE;
    public static final String DARK_BG_HEX = "#1e1e1e";
    public static final String LIGHT_BG_HEX = "#ffffff";

    private static ThemeChangeCallback themeChangeCallback;
    private static Boolean lastKnownIsDark;
    private static boolean listenerRegistered;

    public interface ThemeChangeCallback {
        void onThemeChanged(JsonObject themeConfig);
    }

    private ThemeConfigService() {}

    public static boolean isDarkTheme() {
        try {
            return !JBColor.isBright();
        } catch (Exception ignored) {
            return true;
        }
    }

    public static void registerThemeChangeListener(ThemeChangeCallback callback) {
        themeChangeCallback = callback;
        if (listenerRegistered) {
            return;
        }
        listenerRegistered = true;
        try {
            ApplicationManager.getApplication().getMessageBus().connect().subscribe(
                LafManagerListener.TOPIC,
                (LafManagerListener) source -> ApplicationManager.getApplication().invokeLater(ThemeConfigService::notifyThemeChange)
            );
        } catch (Exception e) {
            LOG.warn("[ThemeConfig] Failed to register theme listener: " + e.getMessage(), e);
        }
    }

    private static void notifyThemeChange() {
        if (themeChangeCallback == null) {
            return;
        }
        JsonObject config = getIdeThemeConfig();
        boolean isDark = config.get("isDark").getAsBoolean();
        if (lastKnownIsDark != null && lastKnownIsDark == isDark) {
            return;
        }
        lastKnownIsDark = isDark;
        themeChangeCallback.onThemeChanged(config);
    }

    public static JsonObject getIdeThemeConfig() {
        JsonObject config = new JsonObject();
        config.addProperty("isDark", isDarkTheme());
        return config;
    }

    public static String getIdeThemeConfigJson() {
        JsonObject config = getIdeThemeConfig();
        lastKnownIsDark = config.get("isDark").getAsBoolean();
        return new Gson().toJson(config);
    }

    public static Color getBackgroundColor() {
        return isDarkTheme() ? DARK_BG_COLOR : LIGHT_BG_COLOR;
    }

    public static String getBackgroundColorHex() {
        return isDarkTheme() ? DARK_BG_HEX : LIGHT_BG_HEX;
    }
}
