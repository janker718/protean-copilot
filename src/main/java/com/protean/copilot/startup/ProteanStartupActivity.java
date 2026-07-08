package com.protean.copilot.startup;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.protean.copilot.notifications.ProteanNotifier;
import com.protean.copilot.settings.manager.ProviderManager;
import com.protean.copilot.settings.manager.WorkingDirectoryManager;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ProteanStartupActivity implements ProjectActivity {

    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        ProviderManager.getInstance(project).getActiveProvider();
        WorkingDirectoryManager.getInstance(project).resolveWorkingDirectory();
        ProteanNotifier.info(project, "Protean Copilot initialized");
        return Unit.INSTANCE;
    }
}
