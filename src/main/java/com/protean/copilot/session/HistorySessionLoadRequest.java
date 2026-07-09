package com.protean.copilot.session;

import com.protean.copilot.settings.TabStateService;
import org.jetbrains.annotations.Nullable;

/**
 * Stable restore contract shared by window, lifecycle and cross-window loaders.
 */
public record HistorySessionLoadRequest(
    String sessionId,
    @Nullable String projectPath,
    String provider
) {

    public HistorySessionLoadRequest {
        sessionId = normalizeSessionId(sessionId);
        projectPath = normalize(projectPath);
        provider = normalizeProvider(provider);
    }

    public static HistorySessionLoadRequest of(String sessionId, @Nullable String projectPath, @Nullable String provider) {
        return new HistorySessionLoadRequest(sessionId, projectPath, provider);
    }

    public static @Nullable HistorySessionLoadRequest fromTabState(@Nullable TabStateService.TabSessionState state) {
        if (state == null) {
            return null;
        }
        String normalizedSessionId = normalizeSessionId(state.sessionId());
        if (normalizedSessionId == null) {
            return null;
        }
        return new HistorySessionLoadRequest(normalizedSessionId, state.cwd(), state.provider());
    }

    private static @Nullable String normalizeSessionId(@Nullable String value) {
        return normalize(value);
    }

    private static @Nullable String normalize(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeProvider(@Nullable String provider) {
        String normalized = normalize(provider);
        return normalized == null ? "claude" : normalized;
    }
}
