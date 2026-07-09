package com.protean.copilot.settings.manager;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.protean.copilot.settings.SettingsService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.PROJECT)
public final class ProviderManager {

    public static final String DEFAULT_PROVIDER = "claude";

    public static ProviderManager getInstance(@NotNull Project project) {
        return project.getService(ProviderManager.class);
    }

    private final SettingsService settingsService = new SettingsService();

    public @NotNull String getActiveProvider() {
        return normalizeProvider(settingsService.getProvider());
    }

    public void setActiveProvider(@NotNull String provider) {
        settingsService.setProvider(normalizeProvider(provider));
    }

    public @NotNull String resolveProvider(@Nullable String provider) {
        return normalizeProvider(provider);
    }

    private static @NotNull String normalizeProvider(@Nullable String provider) {
        if (provider == null) {
            return DEFAULT_PROVIDER;
        }
        String trimmed = provider.trim();
        if (trimmed.isEmpty()) {
            return DEFAULT_PROVIDER;
        }
        return trimmed;
    }
}
