package com.protean.copilot.dependency;

public record UpdateInfo(
    String sdkId,
    String sdkName,
    boolean hasUpdate,
    String currentVersion,
    String latestVersion,
    String error
) {
    public static UpdateInfo upToDate(String sdkId, String sdkName, String currentVersion) {
        return new UpdateInfo(sdkId, sdkName, false, currentVersion, currentVersion, null);
    }

    public static UpdateInfo updateAvailable(String sdkId, String sdkName, String currentVersion, String latestVersion) {
        return new UpdateInfo(sdkId, sdkName, true, currentVersion, latestVersion, null);
    }

    public static UpdateInfo error(String sdkId, String sdkName, String error) {
        return new UpdateInfo(sdkId, sdkName, false, null, null, error);
    }
}
