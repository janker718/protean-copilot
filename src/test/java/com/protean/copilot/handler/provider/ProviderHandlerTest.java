package com.protean.copilot.handler.provider;

import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.settings.CodemossSettingsService;
import org.junit.Test;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ProviderHandlerTest {

    @Test
    public void addProviderIsConsumedAndRefreshesTheWebviewList() throws Exception {
        List<String> callbacks = new ArrayList<>();
        HandlerContext context = new HandlerContext(null, null, null, new HandlerContext.JsCallback() {
            @Override
            public void callJavaScript(String functionName, String... args) {
                callbacks.add(functionName + ":" + args[0]);
            }

            @Override
            public String escapeJs(String str) {
                return str;
            }
        });
        ProviderHandler handler = new ProviderHandler(
            context,
            new CodemossSettingsService(Files.createTempDirectory("provider-handler-"))
        );

        assertTrue(handler.handle("add_provider", "{\"id\":\"provider-a\",\"name\":\"Provider A\"}"));
        assertEquals(1, callbacks.size());
        assertTrue(callbacks.get(0).startsWith("updateProviders:"));
        assertTrue(callbacks.get(0).contains("provider-a"));
    }

    @Test
    public void thinkingSettingIsConsumedAndPersisted() throws Exception {
        List<String> callbacks = new ArrayList<>();
        HandlerContext context = new HandlerContext(null, null, null, new HandlerContext.JsCallback() {
            @Override public void callJavaScript(String functionName, String... args) { callbacks.add(functionName + ":" + args[0]); }
            @Override public String escapeJs(String str) { return str; }
        });
        ProviderHandler handler = new ProviderHandler(
            context,
            new CodemossSettingsService(Files.createTempDirectory("provider-thinking-"))
        );

        assertTrue(handler.handle("set_thinking_enabled", "{\"enabled\":true}"));
        assertTrue(handler.handle("get_thinking_enabled", "{}"));
        assertEquals("updateThinkingEnabled:true", callbacks.get(0));
    }
}
