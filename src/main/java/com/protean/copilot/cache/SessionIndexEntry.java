package com.protean.copilot.cache;

public record SessionIndexEntry(
    String sessionId,
    String summary,
    String provider,
    String workingDirectory,
    long updatedAt,
    int messageCount,
    long fileSize,
    boolean favorited,
    long favoritedAt,
    String customTitle,
    String entrypoint
) {
}
