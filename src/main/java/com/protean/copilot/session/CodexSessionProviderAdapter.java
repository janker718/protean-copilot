package com.protean.copilot.session;

import com.google.gson.JsonObject;
import com.protean.copilot.provider.codex.CodexHistoryReader;
import com.protean.copilot.provider.codex.CodexSDKBridge;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CodexSessionProviderAdapter implements SessionProviderAdapter {

    private final CodexSDKBridge codexBridge;
    private final CodexHistoryReader historyReader;

    public CodexSessionProviderAdapter(@Nullable CodexSDKBridge codexBridge) {
        this(codexBridge, new CodexHistoryReader());
    }

    CodexSessionProviderAdapter(@Nullable CodexSDKBridge codexBridge, @NotNull CodexHistoryReader historyReader) {
        this.codexBridge = codexBridge;
        this.historyReader = historyReader;
    }

    @Override
    public @NotNull String providerId() {
        return "codex";
    }

    @Override
    public @NotNull CompletableFuture<String> launchChannel(@Nullable String channelId, @Nullable String sessionId, @Nullable String cwd) {
        if (codexBridge == null || !codexBridge.isRunning()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Codex SDK bridge is not running"));
        }
        return CompletableFuture.completedFuture(
            channelId != null && !channelId.isBlank() ? channelId : UUID.randomUUID().toString()
        );
    }

    @Override
    public @NotNull CompletableFuture<Void> interruptChannel(@Nullable String channelId, @Nullable String sessionId) {
        if (codexBridge != null && codexBridge.isRunning() && sessionId != null && !sessionId.isBlank()) {
            return codexBridge.interrupt(sessionId);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public @NotNull List<JsonObject> getSessionMessages(@NotNull String sessionId, @Nullable String cwd) {
        return historyReader.readSessionMessages(sessionId, cwd);
    }
}
