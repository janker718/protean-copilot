package com.protean.copilot.handler;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.protean.copilot.handler.core.HandlerContext;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

public class UsagePushServiceTest {

    @Test
    public void usageUpdatePassesRawJsonToTheJavascriptBridge() {
        AtomicReference<String> callbackName = new AtomicReference<>();
        AtomicReference<String> callbackArgument = new AtomicReference<>();
        HandlerContext context = new HandlerContext(null, null, null, new HandlerContext.JsCallback() {
            @Override
            public void callJavaScript(String functionName, String... args) {
                callbackName.set(functionName);
                callbackArgument.set(args[0]);
            }

            @Override
            public String escapeJs(String value) {
                return value;
            }
        });

        new UsagePushService(context).pushUsageUpdateAfterModelChange(200_000);

        assertEquals("window.onUsageUpdate", callbackName.get());
        JsonObject payload = JsonParser.parseString(callbackArgument.get()).getAsJsonObject();
        assertEquals(0, payload.get("percentage").getAsInt());
        assertEquals(200_000, payload.get("maxTokens").getAsInt());
    }
}
