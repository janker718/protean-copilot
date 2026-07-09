package com.protean.copilot.util;

import com.google.gson.JsonObject;
import com.intellij.DynamicBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.protean.copilot.settings.CodemossSettingsService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

public final class LanguageConfigService {

    private static final Logger LOG = Logger.getInstance(LanguageConfigService.class);
    private static final Set<String> SUPPORTED_LANGUAGES = Set.of(
        "zh", "en", "zh-TW", "hi", "es", "fr", "ja", "ru", "ko", "pt-BR"
    );

    private LanguageConfigService() {}

    public static @Nullable String getUserLanguage(@Nullable CodemossSettingsService settingsService) {
        if (settingsService == null) {
            return null;
        }
        try {
            String value = settingsService.getUserLanguage();
            if (value == null || value.isBlank() || !SUPPORTED_LANGUAGES.contains(value)) {
                return null;
            }
            return value;
        } catch (Exception e) {
            LOG.warn("[LanguageConfig] Failed to read user language: " + e.getMessage());
            return null;
        }
    }

    public static void setUserLanguage(@NotNull CodemossSettingsService settingsService, @Nullable String language)
        throws IOException {
        if (language == null || !SUPPORTED_LANGUAGES.contains(language.trim())) {
            throw new IllegalArgumentException("Unsupported language: " + language);
        }
        settingsService.setUserLanguage(language.trim());
    }

    public static void clearUserLanguage(@NotNull CodemossSettingsService settingsService) throws IOException {
        settingsService.clearUserLanguage();
    }

    public static JsonObject getLanguageConfig(@Nullable CodemossSettingsService settingsService) {
        JsonObject config = new JsonObject();
        try {
            String userLanguage = getUserLanguage(settingsService);
            if (userLanguage != null) {
                config.addProperty("language", userLanguage);
                config.addProperty("source", "user");
                config.addProperty("ideaLocale", "");
                return config;
            }
            Locale locale = DynamicBundle.getLocale();
            config.addProperty("language", mapIdeaLocaleToI18n(locale));
            config.addProperty("source", "idea");
            config.addProperty("ideaLocale", locale != null ? locale.toString() : "");
            return config;
        } catch (Exception e) {
            LOG.warn("[LanguageConfig] Falling back to English: " + e.getMessage());
            config.addProperty("language", "en");
            config.addProperty("source", "fallback");
            config.addProperty("ideaLocale", "en");
            return config;
        }
    }

    private static String mapIdeaLocaleToI18n(@Nullable Locale locale) {
        if (locale == null) {
            return "en";
        }
        String language = locale.getLanguage();
        String country = locale.getCountry();
        if ("zh".equals(language)) {
            return ("TW".equals(country) || "HK".equals(country)) ? "zh-TW" : "zh";
        }
        return switch (language) {
            case "en", "hi", "es", "fr", "ja", "ru", "ko" -> language;
            case "pt" -> "pt-BR";
            default -> "en";
        };
    }
}
