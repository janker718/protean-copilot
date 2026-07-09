package com.protean.copilot.session;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.protean.copilot.provider.claude.ClaudeHistoryReader;
import com.protean.copilot.provider.claude.ClaudeSDKBridge;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Claude implementation of the provider session transport contract.
 */
public final class ClaudeSessionProviderAdapter implements SessionProviderAdapter {

    private static final Gson GSON = new Gson();

    private final ClaudeSDKBridge claudeBridge;
    private final ClaudeHistoryReader historyReader;

    public ClaudeSessionProviderAdapter(@Nullable ClaudeSDKBridge claudeBridge) {
        this(claudeBridge, new ClaudeHistoryReader());
    }

    ClaudeSessionProviderAdapter(@Nullable ClaudeSDKBridge claudeBridge, @NotNull ClaudeHistoryReader historyReader) {
        this.claudeBridge = claudeBridge;
        this.historyReader = historyReader;
    }

    @Override
    public @NotNull String providerId() {
        return "claude";
    }

    @Override
    public @NotNull CompletableFuture<String> launchChannel(
        @Nullable String channelId,
        @Nullable String sessionId,
        @Nullable String cwd
    ) {
        if (claudeBridge == null || !claudeBridge.isRunning()) {
            return CompletableFuture.failedFuture(new IllegalStateException("Claude SDK bridge is not running"));
        }
        if (channelId != null && !channelId.isBlank()) {
            return CompletableFuture.completedFuture(channelId);
        }
        return CompletableFuture.completedFuture(UUID.randomUUID().toString());
    }

    @Override
    public @NotNull CompletableFuture<Void> interruptChannel(@Nullable String channelId, @Nullable String sessionId) {
        if (channelId == null || channelId.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        if (claudeBridge != null && claudeBridge.isRunning() && sessionId != null && !sessionId.isBlank()) {
            return claudeBridge.interrupt(sessionId);
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public @NotNull List<JsonObject> getSessionMessages(@NotNull String sessionId, @Nullable String cwd) {
        List<ClaudeHistoryReader.ConversationMessage> rawMessages = historyReader.readSessionMessages(cwd, sessionId);
        List<JsonObject> messages = new ArrayList<>();
        for (ClaudeHistoryReader.ConversationMessage rawMessage : rawMessages) {
            messages.add(GSON.toJsonTree(rawMessage).getAsJsonObject());
        }
        return messages;
    }
}
