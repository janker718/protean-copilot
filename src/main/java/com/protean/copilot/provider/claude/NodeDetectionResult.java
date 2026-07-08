package com.protean.copilot.provider.claude;

/**
 * Node.js 检测结果。
 */
public record NodeDetectionResult(
    String nodePath,
    String version,
    String npmVersion,
    boolean available
) {
    public static NodeDetectionResult success(String path, String version, String npmVersion) {
        return new NodeDetectionResult(path, version, npmVersion, true);
    }

    public static NodeDetectionResult failure(String path, String error) {
        return new NodeDetectionResult(path, error, null, false);
    }
}
