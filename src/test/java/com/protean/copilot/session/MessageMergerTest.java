package com.protean.copilot.session;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MessageMergerTest {

    private final MessageMerger merger = new MessageMerger();

    @Test
    public void mergeAssistantMessage_preservesToolBlocksWhileGrowingText() {
        JsonObject existing = JsonParser.parseString("""
            {
              "type": "assistant",
              "message": {
                "content": [
                  {"type":"text","text":"Hello"},
                  {"type":"tool_use","id":"tool-1","name":"read_file"},
                  {"type":"text","text":"After tool"}
                ]
              }
            }
            """).getAsJsonObject();

        JsonObject incoming = JsonParser.parseString("""
            {
              "type": "assistant",
              "message": {
                "content": [
                  {"type":"text","text":"Hello world"},
                  {"type":"text","text":"After tool extended"}
                ]
              }
            }
            """).getAsJsonObject();

        JsonObject merged = merger.mergeAssistantMessage(existing, incoming);

        assertEquals(3, merged.getAsJsonObject("message").getAsJsonArray("content").size());
        assertEquals("Hello world",
            merged.getAsJsonObject("message").getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString());
        assertEquals("tool_use",
            merged.getAsJsonObject("message").getAsJsonArray("content").get(1).getAsJsonObject().get("type").getAsString());
        assertEquals("After tool extended",
            merged.getAsJsonObject("message").getAsJsonArray("content").get(2).getAsJsonObject().get("text").getAsString());
    }
}
