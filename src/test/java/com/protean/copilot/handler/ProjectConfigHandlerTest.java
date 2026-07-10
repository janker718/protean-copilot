package com.protean.copilot.handler;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.protean.copilot.handler.core.HandlerContext;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

public class ProjectConfigHandlerTest {

    @Test
    public void pushJsonPassesRawStreamingConfigToJavascriptBridge() throws Exception {
        AtomicReference<String> callback = new AtomicReference<>();
        AtomicReference<String> argument = new AtomicReference<>();
        HandlerContext context = new HandlerContext(null, null, null, new HandlerContext.JsCallback() {
            @Override
            public void callJavaScript(String functionName, String... args) {
                callback.set(functionName);
                argument.set(args[0]);
            }

            @Override
            public String escapeJs(String value) {
                return value;
            }
        });
        JsonObject payload = new JsonObject();
        payload.addProperty("streamingEnabled", true);

        ProjectConfigHandler.pushJson(context, "window.updateStreamingEnabled", payload);

        assertEquals("window.updateStreamingEnabled", callback.get());
        assertEquals(true, JsonParser.parseString(argument.get()).getAsJsonObject()
            .get("streamingEnabled").getAsBoolean());
    }
}
