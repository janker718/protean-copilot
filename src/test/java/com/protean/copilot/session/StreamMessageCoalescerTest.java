package com.protean.copilot.session;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class StreamMessageCoalescerTest {

    @Test
    public void callbackPayloadKeepsMessageJsonUnescapedUntilJcefBoundary() {
        String messagesJson = "[{\"role\":\"assistant\",\"content\":\"A quote: \\\" and a newline\\n\","
            + "\"raw\":{\"type\":\"message\",\"metadata\":{\"source\":\"codex\"}}}]";

        String callbackPayload = StreamMessageCoalescer.prepareCallbackPayload(messagesJson);

        assertEquals(messagesJson, callbackPayload);
        JsonArray messages = JsonParser.parseString(callbackPayload).getAsJsonArray();
        assertEquals("A quote: \" and a newline\n", messages.get(0).getAsJsonObject().get("content").getAsString());
    }
}
