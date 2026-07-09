package com.protean.copilot.session;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Provider-specific session transport contract.
 */
public interface SessionProviderAdapter {

    @NotNull String providerId();

    @NotNull CompletableFuture<String> launchChannel(
        @Nullable String channelId,
        @Nullable String sessionId,
        @Nullable String cwd
    );

    @NotNull CompletableFuture<Void> interruptChannel(
        @Nullable String channelId,
        @Nullable String sessionId
    );

    @NotNull List<JsonObject> getSessionMessages(@NotNull String sessionId, @Nullable String cwd);
}
