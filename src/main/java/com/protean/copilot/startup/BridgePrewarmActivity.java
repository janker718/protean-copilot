package com.protean.copilot.startup;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.protean.copilot.bridge.NodeDetector;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BridgePrewarmActivity implements ProjectActivity {

    private static final Logger LOG = Logger.getInstance(BridgePrewarmActivity.class);

    @Override
    public @Nullable Object execute(@NotNull Project project, @NotNull Continuation<? super Unit> continuation) {
        try {
            NodeDetector.getInstance().detectNodeWithDetails();
        } catch (Exception ex) {
            LOG.warn("Bridge prewarm failed", ex);
        }
        return Unit.INSTANCE;
    }
}
