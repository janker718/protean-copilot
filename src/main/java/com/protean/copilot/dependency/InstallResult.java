package com.protean.copilot.dependency;

public record InstallResult(
    boolean success,
    String sdkId,
    String installedVersion,
    String requestedVersion,
    String error,
    String logs
) {
    public static InstallResult success(String sdkId, String installedVersion, String requestedVersion, String logs) {
        return new InstallResult(true, sdkId, installedVersion, requestedVersion, null, logs);
    }

    public static InstallResult failure(String sdkId, String requestedVersion, String error, String logs) {
        return new InstallResult(false, sdkId, null, requestedVersion, error, logs);
    }
}
