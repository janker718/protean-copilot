package com.protean.copilot.handler;

import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.handler.core.MessageDispatcher;
import com.protean.copilot.settings.CodemossSettingsService;
import org.junit.Test;

import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FrontendActionCoverageHandlerTest {

    @Test
    public void allSupplementalWebviewActionsAreConsumed() throws Exception {
        HandlerContext context = new HandlerContext(null, null, null, new HandlerContext.JsCallback() {
            @Override public void callJavaScript(String functionName, String... args) { }
            @Override public String escapeJs(String value) { return value; }
        });
        FrontendActionCoverageHandler handler = new FrontendActionCoverageHandler(
            context, new CodemossSettingsService(Files.createTempDirectory("frontend-actions-"))
        );
        MessageDispatcher dispatcher = new MessageDispatcher();
        dispatcher.registerHandler(handler);

        for (String type : handler.getSupportedTypes()) {
            assertTrue("Unconsumed WebView action: " + type,
                dispatcher.dispatch(type, payloadFor(type)));
        }
    }

    @Test
    public void selectedAgentUsesTheRegisteredCallbackAndReturnsTheAgentPayload() throws Exception {
        AtomicReference<String> callbackName = new AtomicReference<>();
        AtomicReference<String> callbackPayload = new AtomicReference<>();
        HandlerContext context = new HandlerContext(null, null, null, new HandlerContext.JsCallback() {
            @Override public void callJavaScript(String functionName, String... args) {
                callbackName.set(functionName);
                callbackPayload.set(args[0]);
            }
            @Override public String escapeJs(String value) { return value; }
        });
        CodemossSettingsService settings = new CodemossSettingsService(Files.createTempDirectory("selected-agent-"));
        settings.addAgent(com.google.gson.JsonParser.parseString(
            "{\"id\":\"agent\",\"name\":\"Agent\",\"prompt\":\"Help\"}"
        ).getAsJsonObject());
        settings.setSelectedAgentId("agent");

        FrontendActionCoverageHandler handler = new FrontendActionCoverageHandler(context, settings);
        assertTrue(handler.handle("get_selected_agent", ""));
        assertEquals("window.updateSelectedAgent", callbackName.get());
        assertEquals("agent", com.google.gson.JsonParser.parseString(callbackPayload.get())
            .getAsJsonObject().get("id").getAsString());
    }

    private static String payloadFor(String type) {
        return switch (type) {
            case "add_agent" -> "{\"id\":\"agent\",\"name\":\"Agent\"}";
            case "update_agent" -> "{\"id\":\"agent\",\"updates\":{\"name\":\"Updated\"}}";
            case "delete_agent", "set_selected_agent" -> "{\"id\":\"agent\"}";
            default -> "{}";
        };
    }
}
