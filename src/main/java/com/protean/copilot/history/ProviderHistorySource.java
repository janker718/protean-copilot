package com.protean.copilot.history;

import com.protean.copilot.cache.SessionIndexEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Provider-owned history source that can enumerate persisted sessions.
 */
public interface ProviderHistorySource {

    @NotNull String providerId();

    @NotNull List<SessionIndexEntry> listEntries(@Nullable String projectPath, boolean forceRefresh);
}
