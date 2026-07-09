package com.protean.copilot.session;

import com.google.gson.JsonObject;
import com.protean.copilot.bridge.SdkBridge;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Provider router aligned with the reference project's session routing boundary.
 */
public class SessionProviderRouter implements SessionMessageOrchestrator.SessionHistoryAccess {

    private final Map<String, SessionProviderAdapter> adaptersByProvider;

    public SessionProviderRouter(SdkBridge sdkBridge) {
        this(List.of(
            new ClaudeSessionProviderAdapter(sdkBridge.getClaudeBridge()),
            new CodexSessionProviderAdapter(sdkBridge.getCodexBridge())
        ));
    }

    SessionProviderRouter(List<SessionProviderAdapter> adapters) {
        this.adaptersByProvider = adapters.stream().collect(Collectors.toUnmodifiableMap(
            adapter -> normalizeProvider(adapter.providerId()),
            Function.identity()
        ));
    }

    public CompletableFuture<String> launchChannel(String provider, String channelId, String sessionId, String cwd) {
        return requireAdapter(provider).launchChannel(channelId, sessionId, cwd);
    }

    public CompletableFuture<Void> interruptChannel(String provider, String channelId, String sessionId) {
        return requireAdapter(provider).interruptChannel(channelId, sessionId);
    }

    @Override
    public List<JsonObject> getProviderSessionMessages(String provider, String sessionId, String cwd) {
        if (sessionId == null || sessionId.isBlank()) {
            return new ArrayList<>();
        }
        return requireAdapter(provider).getSessionMessages(sessionId, cwd);
    }

    public List<JsonObject> getSessionMessages(String provider, String sessionId, String cwd) {
        return getProviderSessionMessages(provider, sessionId, cwd);
    }

    private @NotNull SessionProviderAdapter requireAdapter(@Nullable String provider) {
        String normalizedProvider = normalizeProvider(provider);
        SessionProviderAdapter adapter = adaptersByProvider.get(normalizedProvider);
        if (adapter != null) {
            return adapter;
        }
        throw new IllegalArgumentException("Unsupported provider: " + normalizedProvider);
    }

    private static String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return "claude";
        }
        return provider.trim();
    }
}
