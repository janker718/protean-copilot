package com.protean.copilot.provider.claude;

import com.protean.copilot.cache.ClaudeHistoryIndexStore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ClaudeHistoryIndexServiceTest {

    private static final String UUID_1 = "aaaaaaaa-1111-4111-8111-111111111111";
    private static final String UUID_2 = "bbbbbbbb-2222-4222-8222-222222222222";
    private static final String UUID_3 = "cccccccc-3333-4333-8333-333333333333";

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void incrementalScan_restoresUnchangedEntries_andRereadsChangedFile() throws IOException {
        Path projectDir = tmp.newFolder("claude-index-incremental").toPath();
        Path cacheDir = tmp.newFolder("claude-index-cache").toPath();

        Path fileA = writeSession(projectDir, UUID_1, "Hello A", "2026-07-09T10:00:00Z", null);
        Path fileB = writeSession(projectDir, UUID_2, "Hello B", "2026-07-09T10:05:00Z", null);

        long mtimeA = Files.getLastModifiedTime(fileA).toMillis();
        long mtimeB = Files.getLastModifiedTime(fileB).toMillis();

        ClaudeHistoryIndexStore.ProjectIndex existing = new ClaudeHistoryIndexStore.ProjectIndex();
        existing.projectPath = "/tmp/project";
        existing.sessions.add(entry(
            UUID_1, "STALE A", 1, mtimeA + 1000, Files.size(fileA), mtimeA + 1000, UUID_1 + ".jsonl", ""
        ));
        existing.sessions.add(entry(
            UUID_2, "Hello B", 1, mtimeB, Files.size(fileB), mtimeB, UUID_2 + ".jsonl", ""
        ));

        ClaudeHistoryIndexService service = newService(projectDir, cacheDir);
        ClaudeHistoryIndexService.ScanResult result = service.incrementalScanLite(projectDir, existing);

        Map<String, ClaudeHistoryReader.SessionInfo> byId = result.sessions().stream()
            .collect(Collectors.toMap(session -> session.sessionId, session -> session));

        assertEquals(2, byId.size());
        assertEquals("Hello A", byId.get(UUID_1).title);
        assertEquals("Hello B", byId.get(UUID_2).title);
        assertEquals(Long.valueOf(mtimeA), result.sessionMtimes().get(UUID_1));
        assertEquals(Long.valueOf(mtimeB), result.sessionMtimes().get(UUID_2));
    }

    @Test
    public void incrementalScan_discoversNewSession_andHealsLegacyEntrypoint() throws IOException {
        Path projectDir = tmp.newFolder("claude-index-heal").toPath();
        Path cacheDir = tmp.newFolder("claude-index-heal-cache").toPath();

        Path fileA = writeSession(projectDir, UUID_1, "Existing", "2026-07-09T10:00:00Z", "sdk-cli");
        long mtimeA = Files.getLastModifiedTime(fileA).toMillis();
        writeSession(projectDir, UUID_3, "Brand New", "2026-07-09T11:00:00Z", null);
        Files.writeString(projectDir.resolve("backup-file.jsonl"), "{\"type\":\"user\"}\n");

        ClaudeHistoryIndexStore.ProjectIndex existing = new ClaudeHistoryIndexStore.ProjectIndex();
        existing.projectPath = "/tmp/project";
        existing.sessions.add(entry(
            UUID_1, "Existing", 1, mtimeA, Files.size(fileA), mtimeA, UUID_1 + ".jsonl", null
        ));

        ClaudeHistoryIndexService service = newService(projectDir, cacheDir);
        ClaudeHistoryIndexService.ScanResult result = service.incrementalScanLite(projectDir, existing);

        Map<String, ClaudeHistoryReader.SessionInfo> byId = result.sessions().stream()
            .collect(Collectors.toMap(session -> session.sessionId, session -> session));

        assertEquals(2, byId.size());
        assertEquals("sdk-cli", byId.get(UUID_1).entrypoint);
        assertNotNull(byId.get(UUID_3));
        assertTrue(result.sessionMtimes().containsKey(UUID_3));
        assertNull(result.sessionMtimes().get("backup-file"));
    }

    private ClaudeHistoryIndexService newService(Path projectDir, Path cacheDir) {
        return new ClaudeHistoryIndexService(
            projectDir,
            new ClaudeHistoryParser(),
            new ClaudeHistoryIndexStore(cacheDir)
        );
    }

    private Path writeSession(
        Path projectDir,
        String sessionId,
        String firstUserText,
        String timestamp,
        String entrypoint
    ) throws IOException {
        Path file = projectDir.resolve(sessionId + ".jsonl");
        String entrypointJson = entrypoint == null ? "" : ",\"entrypoint\":\"" + entrypoint + "\"";
        String line = "{\"type\":\"user\"" + entrypointJson + ",\"message\":{\"role\":\"user\",\"content\":\""
            + firstUserText.replace("\"", "\\\"")
            + "\"},\"timestamp\":\"" + timestamp + "\"}\n";
        Files.writeString(file, line);
        return file;
    }

    private ClaudeHistoryIndexStore.IndexedSession entry(
        String sessionId,
        String title,
        int messageCount,
        long lastTimestamp,
        long fileSize,
        long indexedFileMtime,
        String fileRelativePath,
        String entrypoint
    ) {
        ClaudeHistoryIndexStore.IndexedSession entry = new ClaudeHistoryIndexStore.IndexedSession();
        entry.sessionId = sessionId;
        entry.title = title;
        entry.messageCount = messageCount;
        entry.lastTimestamp = lastTimestamp;
        entry.firstTimestamp = lastTimestamp;
        entry.fileSize = fileSize;
        entry.fileLastModified = indexedFileMtime;
        entry.fileRelativePath = fileRelativePath;
        entry.entrypoint = entrypoint;
        return entry;
    }
}
