package com.protean.copilot.cache;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists provider-level Claude history indexes under the IDE system cache.
 */
public final class ClaudeHistoryIndexStore {

    private static final Logger LOG = Logger.getInstance(ClaudeHistoryIndexStore.class);

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path storageDir;

    public ClaudeHistoryIndexStore() {
        this(Path.of(PathManager.getSystemPath(), "protean-copilot", "claude-history-index"));
    }

    public ClaudeHistoryIndexStore(Path storageDir) {
        this.storageDir = storageDir;
    }

    public synchronized ProjectIndex load(String projectPath) {
        if (projectPath == null || projectPath.isBlank()) {
            return null;
        }

        Path file = resolveFile(projectPath);
        if (!Files.exists(file)) {
            return null;
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            ProjectIndex index = gson.fromJson(reader, ProjectIndex.class);
            if (index == null || index.projectPath == null || !projectPath.equals(index.projectPath)) {
                return null;
            }
            if (index.sessions == null) {
                index.sessions = new ArrayList<>();
            }
            return index;
        } catch (Exception e) {
            LOG.warn("Failed to load Claude history index for " + projectPath + ": " + e.getMessage(), e);
            return null;
        }
    }

    public synchronized void save(ProjectIndex index) {
        if (index == null || index.projectPath == null || index.projectPath.isBlank()) {
            return;
        }

        Path file = resolveFile(index.projectPath);
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                gson.toJson(index, writer);
            }
        } catch (IOException e) {
            LOG.warn("Failed to save Claude history index for " + index.projectPath + ": " + e.getMessage(), e);
        }
    }

    private Path resolveFile(String projectPath) {
        return storageDir.resolve(hashProjectPath(projectPath) + ".json");
    }

    private static String hashProjectPath(String projectPath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(projectPath.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            return Integer.toHexString(projectPath.hashCode());
        }
    }

    public static final class ProjectIndex {
        public String projectPath;
        public String sanitizedPath;
        public long lastScanAt;
        public int fileCount;
        public List<IndexedSession> sessions = new ArrayList<>();
    }

    public static final class IndexedSession {
        public String sessionId;
        public String title;
        public int messageCount;
        public long lastTimestamp;
        public long firstTimestamp;
        public long fileSize;
        public String entrypoint;
        public long fileLastModified;
        public String fileRelativePath;
    }
}
