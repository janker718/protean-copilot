package com.protean.copilot.handler;

import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.handler.core.MessageDispatcher;
import com.protean.copilot.settings.CodemossSettingsService;
import org.junit.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
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

    @Test
    public void addPromptPersistsAndPublishesUpdatedPromptList() throws Exception {
        List<String> callbacks = new ArrayList<>();
        List<String> payloads = new ArrayList<>();
        HandlerContext context = new HandlerContext(null, null, null, new HandlerContext.JsCallback() {
            @Override public void callJavaScript(String functionName, String... args) {
                callbacks.add(functionName);
                payloads.add(args.length > 0 ? args[0] : "");
            }
            @Override public String escapeJs(String value) { return value; }
        });
        CodemossSettingsService settings = new CodemossSettingsService(Files.createTempDirectory("prompt-actions-"));
        FrontendActionCoverageHandler handler = new FrontendActionCoverageHandler(context, settings);

        assertTrue(handler.handle("add_prompt", "{\"scope\":\"global\",\"prompt\":{\"id\":\"p1\",\"name\":\"Prompt\",\"content\":\"Hello\"}}"));

        assertEquals("window.promptOperationResult", callbacks.get(0));
        assertEquals("add", com.google.gson.JsonParser.parseString(payloads.get(0)).getAsJsonObject().get("operation").getAsString());
        assertEquals("window.updateGlobalPrompts", callbacks.get(1));
        assertEquals(1, com.google.gson.JsonParser.parseString(payloads.get(1)).getAsJsonArray().size());
    }

    @Test
    public void addMcpServerPersistsAndPublishesServerList() throws Exception {
        AtomicReference<String> callbackName = new AtomicReference<>();
        AtomicReference<String> callbackPayload = new AtomicReference<>();
        HandlerContext context = new HandlerContext(null, null, null, new HandlerContext.JsCallback() {
            @Override public void callJavaScript(String functionName, String... args) {
                callbackName.set(functionName);
                callbackPayload.set(args[0]);
            }
            @Override public String escapeJs(String value) { return value; }
        });
        CodemossSettingsService settings = new CodemossSettingsService(Files.createTempDirectory("mcp-actions-"));
        FrontendActionCoverageHandler handler = new FrontendActionCoverageHandler(context, settings);

        assertTrue(handler.handle("add_mcp_server", "{\"id\":\"fetch\",\"name\":\"fetch\",\"server\":{\"type\":\"stdio\",\"command\":\"uvx\",\"args\":[\"mcp-server-fetch\"]}}"));

        assertEquals("window.updateMcpServers", callbackName.get());
        assertEquals("fetch", com.google.gson.JsonParser.parseString(callbackPayload.get())
            .getAsJsonArray().get(0).getAsJsonObject().get("id").getAsString());
        assertEquals(1, settings.getMcpServers(false).size());
    }

    @Test
    public void saveImportedPromptsSupportsDuplicateStrategyAndPublishesResult() throws Exception {
        List<String> callbacks = new ArrayList<>();
        List<String> payloads = new ArrayList<>();
        HandlerContext context = new HandlerContext(null, null, null, new HandlerContext.JsCallback() {
            @Override public void callJavaScript(String functionName, String... args) {
                callbacks.add(functionName);
                payloads.add(args.length > 0 ? args[0] : "");
            }
            @Override public String escapeJs(String value) { return value; }
        });
        CodemossSettingsService settings = new CodemossSettingsService(Files.createTempDirectory("prompt-import-"));
        settings.addPrompt(false, com.google.gson.JsonParser.parseString(
            "{\"id\":\"p1\",\"name\":\"Prompt\",\"content\":\"Old\"}"
        ).getAsJsonObject());
        FrontendActionCoverageHandler handler = new FrontendActionCoverageHandler(context, settings);

        assertTrue(handler.handle("save_imported_prompts",
            "{\"scope\":\"global\",\"strategy\":\"duplicate\",\"prompts\":[{\"id\":\"p1\",\"name\":\"Prompt\",\"content\":\"New\"}]}"));

        assertEquals("window.promptImportResult", callbacks.get(0));
        assertEquals(1, com.google.gson.JsonParser.parseString(payloads.get(0)).getAsJsonObject().get("imported").getAsInt());
        assertEquals("window.updateGlobalPrompts", callbacks.get(1));
        assertEquals(2, settings.getPrompts(false).size());
    }

    @Test
    public void saveImportedAgentsSupportsOverwriteStrategyAndPublishesResult() throws Exception {
        List<String> callbacks = new ArrayList<>();
        List<String> payloads = new ArrayList<>();
        HandlerContext context = new HandlerContext(null, null, null, new HandlerContext.JsCallback() {
            @Override public void callJavaScript(String functionName, String... args) {
                callbacks.add(functionName);
                payloads.add(args.length > 0 ? args[0] : "");
            }
            @Override public String escapeJs(String value) { return value; }
        });
        CodemossSettingsService settings = new CodemossSettingsService(Files.createTempDirectory("agent-import-"));
        settings.addAgent(com.google.gson.JsonParser.parseString(
            "{\"id\":\"agent\",\"name\":\"Old\",\"prompt\":\"Hello\"}"
        ).getAsJsonObject());
        FrontendActionCoverageHandler handler = new FrontendActionCoverageHandler(context, settings);

        assertTrue(handler.handle("save_imported_agents",
            "{\"strategy\":\"overwrite\",\"agents\":[{\"id\":\"agent\",\"name\":\"New\",\"prompt\":\"Updated\"}]}"));

        assertEquals("window.agentImportResult", callbacks.get(0));
        assertEquals(1, com.google.gson.JsonParser.parseString(payloads.get(0)).getAsJsonObject().get("updated").getAsInt());
        assertEquals("window.updateAgents", callbacks.get(1));
        assertEquals("New", settings.getAgent("agent").get("name").getAsString());
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
