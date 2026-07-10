package com.protean.copilot.handler;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.protean.copilot.handler.core.BaseMessageHandler;
import com.protean.copilot.handler.core.HandlerContext;

import java.util.List;
import java.util.function.Consumer;

/** Routes WebView lifecycle events to the window layer instead of dropping them in generic settings handling. */
public final class WindowEventHandler extends BaseMessageHandler {

    private static final List<String> SUPPORTED_TYPES = List.of(
        "tab_loading_changed", "tab_status_changed", "refresh_slash_commands", "create_new_session"
    );

    private final Consumer<Boolean> loadingChanged;
    private final Consumer<String> statusChanged;
    private final Runnable createNewSession;

    public WindowEventHandler(
        HandlerContext context,
        Consumer<Boolean> loadingChanged,
        Consumer<String> statusChanged,
        Runnable createNewSession
    ) {
        super(context);
        this.loadingChanged = loadingChanged;
        this.statusChanged = statusChanged;
        this.createNewSession = createNewSession;
    }

    @Override
    public boolean handle(String type, String content) {
        if (!SUPPORTED_TYPES.contains(type)) {
            return false;
        }
        JsonObject payload = parse(content);
        switch (type) {
            case "tab_loading_changed" -> loadingChanged.accept(payload.has("loading") && payload.get("loading").getAsBoolean());
            case "tab_status_changed" -> statusChanged.accept(payload.has("status") ? payload.get("status").getAsString() : "idle");
            case "refresh_slash_commands" -> callJavaScript("updateSlashCommands", new com.google.gson.Gson().toJson(
                context.getSession() == null ? List.of() : context.getSession().getSlashCommands()));
            case "create_new_session" -> createNewSession.run();
            default -> { return false; }
        }
        return true;
    }

    @Override
    public List<String> getSupportedTypes() {
        return SUPPORTED_TYPES;
    }

    private static JsonObject parse(String content) {
        if (content == null || content.isBlank()) {
            return new JsonObject();
        }
        var parsed = JsonParser.parseString(content);
        return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
    }
}
