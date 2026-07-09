package com.protean.copilot.permission;

import com.google.gson.JsonObject;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Gateway factories for current session-bound permission services.
 */
public final class PermissionGateways {

    private PermissionGateways() {
    }

    public static @NotNull PermissionGateway fromService(@Nullable PermissionService permissionService) {
        return (toolName, inputs, suggestions, ownerProject) -> {
            if (permissionService == null) {
                return CompletableFuture.completedFuture(new PermissionRequest.PermissionResult(
                    PermissionRequest.PermissionResult.Behavior.ALLOW,
                    null,
                    null,
                    null,
                    false
                ));
            }
            return permissionService.requestLocalPermission(toolName, inputs, suggestions, ownerProject);
        };
    }

    public static @NotNull PermissionGateway resolve(@NotNull Project project, @Nullable PermissionService permissionService) {
        PermissionService resolved = permissionService != null ? permissionService : PermissionService.findRegisteredInstance(project);
        return fromService(resolved);
    }
}
