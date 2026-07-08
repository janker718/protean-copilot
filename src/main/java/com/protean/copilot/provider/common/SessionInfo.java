package com.protean.copilot.provider.common;

public record SessionInfo(
    String provider,
    String sessionId,
    String workingDirectory,
    String model,
    String permissionMode
) {
}
