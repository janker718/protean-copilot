package com.protean.copilot.provider.claude;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class ClaudeUsageAggregatorTest {

    private static final String PROJECT_PATH = "/tmp/demo-project";
    private static final String SESSION_ID = "aaaaaaaa-1111-4111-8111-111111111111";

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void aggregatesProjectUsage_withoutDoubleCountingDuplicatedAssistantBlocks() throws IOException {
        Path projectsDir = tmp.newFolder("claude-projects").toPath();
        Path projectDir = projectsDir.resolve("-tmp-demo-project");
        Files.createDirectories(projectDir);

        String content = ""
            + "{\"type\":\"user\",\"message\":{\"role\":\"user\",\"content\":\"Summarize this repo\"},\"timestamp\":\"2026-07-09T10:00:00Z\"}\n"
            + "{\"type\":\"assistant\",\"message\":{\"id\":\"msg-1\",\"role\":\"assistant\",\"model\":\"claude-sonnet-4-6\",\"usage\":{\"input_tokens\":100,\"output_tokens\":50,\"cache_creation_input_tokens\":10,\"cache_read_input_tokens\":5}},\"timestamp\":\"2026-07-09T10:00:01Z\"}\n"
            + "{\"type\":\"assistant\",\"message\":{\"id\":\"msg-1\",\"role\":\"assistant\",\"model\":\"claude-sonnet-4-6\",\"usage\":{\"input_tokens\":100,\"output_tokens\":50,\"cache_creation_input_tokens\":10,\"cache_read_input_tokens\":5}},\"timestamp\":\"2026-07-09T10:00:01Z\"}\n"
            + "{\"type\":\"assistant\",\"message\":{\"id\":\"msg-2\",\"role\":\"assistant\",\"model\":\"claude-opus-4-5\",\"usage\":{\"input_tokens\":200,\"output_tokens\":40,\"cache_creation_input_tokens\":0,\"cache_read_input_tokens\":0}},\"timestamp\":\"2026-07-09T10:00:02Z\"}\n";
        Files.writeString(projectDir.resolve(SESSION_ID + ".jsonl"), content);

        ClaudeUsageAggregator aggregator = new ClaudeUsageAggregator(projectsDir, new ClaudeHistoryParser());
        ClaudeHistoryReader.ProjectStatistics stats = aggregator.getProjectStatistics(PROJECT_PATH, 0L);

        assertEquals(1, stats.totalSessions);
        assertEquals(405L, stats.totalUsage.totalTokens);
        assertEquals(300L, stats.totalUsage.inputTokens);
        assertEquals(90L, stats.totalUsage.outputTokens);
        assertEquals(10L, stats.totalUsage.cacheWriteTokens);
        assertEquals(5L, stats.totalUsage.cacheReadTokens);
        assertEquals(1, stats.byModel.size());
        assertNotNull(stats.sessions);
        assertEquals("claude-opus-4-5", stats.sessions.get(0).model);
        assertEquals("claude-opus-4-5", stats.byModel.get(0).model);
        assertEquals(405L, stats.byModel.get(0).totalTokens);
        assertEquals(1, stats.dailyUsage.size());
        assertEquals(1, stats.dailyUsage.get(0).sessions);
    }
}
