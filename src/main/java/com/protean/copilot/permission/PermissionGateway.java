package com.protean.copilot.permission;

import com.google.gson.JsonObject;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Provider-agnostic permission evaluation gateway.
 */
public interface PermissionGateway {

    @NotNull CompletableFuture<PermissionRequest.PermissionResult> requestPermission(
        @NotNull String toolName,
        @Nullable JsonObject inputs,
        @Nullable JsonObject suggestions,
        @Nullable Project ownerProject
    );
}
