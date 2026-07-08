package com.protean.copilot.settings.manager;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.protean.copilot.settings.SettingsService;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.PROJECT)
public final class ProviderManager {

    public static ProviderManager getInstance(@NotNull Project project) {
        return project.getService(ProviderManager.class);
    }

    private final SettingsService settingsService = new SettingsService();

    public @NotNull String getActiveProvider() {
        return settingsService.getProvider();
    }

    public void setActiveProvider(@NotNull String provider) {
        settingsService.setProvider(provider);
    }
}
