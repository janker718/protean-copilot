package com.protean.copilot.handler;

import com.intellij.openapi.diagnostic.Logger;
import com.protean.copilot.handler.core.BaseMessageHandler;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.handler.history.HistoryDeleteService;
import com.protean.copilot.handler.history.HistoryExportBridgeService;
import com.protean.copilot.handler.history.HistoryLoadService;
import com.protean.copilot.handler.history.HistoryMetadataBridgeService;
import com.protean.copilot.handler.history.HistoryMessageInjector;
import com.protean.copilot.handler.history.HistorySessionConversionService;
import com.protean.copilot.session.SessionLifecycleManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * History data handler.
 * Routes history-related messages to dedicated service classes.
 */
public final class HistoryHandler extends BaseMessageHandler {

    private static final Logger LOG = Logger.getInstance(HistoryHandler.class);

    private static final List<String> SUPPORTED_TYPES = List.of(
        "load_history_data",
        "load_session",
        "delete_session",
        "delete_sessions",
        "export_session",
        "toggle_favorite",
        "update_title",
        "delete_title",
        "deep_search_history",
        "convert_to_cli_session"
    );

    private String currentProvider = "claude";

    private final HistoryLoadService historyLoadService;
    private final HistoryDeleteService historyDeleteService;
    private final HistoryMessageInjector historyMessageInjector;
    private final HistoryExportBridgeService historyExportService;
    private final HistoryMetadataBridgeService historyMetadataService;
    private final HistorySessionConversionService sessionConversionService;

    public HistoryHandler(
        @NotNull HandlerContext context,
        @NotNull SessionLifecycleManager sessionLifecycleManager
    ) {
        super(context);
        this.historyLoadService = new HistoryLoadService(context);
        this.historyDeleteService = new HistoryDeleteService(context);
        this.historyMessageInjector = new HistoryMessageInjector(context, sessionLifecycleManager);
        this.historyExportService = new HistoryExportBridgeService(context);
        this.historyMetadataService = new HistoryMetadataBridgeService(context);
        this.sessionConversionService = new HistorySessionConversionService(context);
    }

    @Override
    public boolean handle(String type, String content) {
        switch (type) {
            case "load_history_data":
                currentProvider = normalizeProvider(content);
                historyLoadService.handleLoadHistoryData(currentProvider);
                return true;
            case "load_session":
                historyMessageInjector.handleLoadSession(content, currentProvider);
                return true;
            case "delete_session":
                historyDeleteService.handleDeleteSession(content);
                return true;
            case "delete_sessions":
                historyDeleteService.handleDeleteSessions(content);
                return true;
            case "export_session":
                historyExportService.handleExportSession(content);
                return true;
            case "toggle_favorite":
                historyMetadataService.handleToggleFavorite(content);
                return true;
            case "update_title":
                historyMetadataService.handleUpdateTitle(content);
                return true;
            case "delete_title":
                historyMetadataService.handleDeleteTitle(content);
                return true;
            case "deep_search_history":
                currentProvider = normalizeProvider(content);
                historyLoadService.handleDeepSearchHistory(currentProvider);
                return true;
            case "convert_to_cli_session":
                sessionConversionService.handleConvertToCliSession(content);
                return true;
            default:
                return false;
        }
    }

    @Override
    public List<String> getSupportedTypes() {
        return SUPPORTED_TYPES;
    }

    private static String normalizeProvider(String provider) {
        if (provider == null) {
            return "claude";
        }
        String trimmed = provider.trim();
        return trimmed.isEmpty() ? "claude" : trimmed;
    }
}
