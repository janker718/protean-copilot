package com.protean.copilot.session;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ReplayDeduplicatorTest {

    @Test
    public void consumeContentDelta_stripsAlreadyReplayedPrefix() {
        ReplayDeduplicator deduplicator = new ReplayDeduplicator();
        deduplicator.beginContentReplay("Hello world", 5);

        assertEquals("", deduplicator.consumeContentDelta(" world"));
        assertEquals("!", deduplicator.consumeContentDelta("!"));
    }

    @Test
    public void extractContentHelpers_readTextAndThinkingBlocks() {
        JsonObject raw = JsonParser.parseString("""
            {
              "message": {
                "content": [
                  {"type":"thinking","thinking":"plan"},
                  {"type":"text","text":"answer"}
                ]
              }
            }
            """).getAsJsonObject();

        assertEquals("plan", ReplayDeduplicator.extractThinkingContent(raw));
        assertEquals("answer", ReplayDeduplicator.extractTextContent(raw));
    }
}
