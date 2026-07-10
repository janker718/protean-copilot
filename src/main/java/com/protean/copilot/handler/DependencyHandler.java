package com.protean.copilot.handler;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.protean.copilot.bridge.NodeDetector;
import com.protean.copilot.dependency.DependencyManager;
import com.protean.copilot.dependency.InstallResult;
import com.protean.copilot.dependency.SdkDefinition;
import com.protean.copilot.dependency.UpdateInfo;
import com.protean.copilot.handler.core.BaseMessageHandler;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.provider.claude.NodeDetectionResult;
import com.protean.copilot.settings.SettingsService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DependencyHandler extends BaseMessageHandler {

    private static final Logger LOG = Logger.getInstance(DependencyHandler.class);

    private static final List<String> SUPPORTED_TYPES = List.of(
        "get_dependency_status",
        "install_dependency",
        "uninstall_dependency",
        "update_dependency",
        "check_dependency_updates",
        "get_dependency_versions",
        "check_node_environment"
    );

    private final Gson gson = new Gson();
    private final SettingsService settingsService = new SettingsService();
    private final NodeDetector nodeDetector = NodeDetector.getInstance();
    private final DependencyManager dependencyManager = new DependencyManager(nodeDetector);

    public DependencyHandler(HandlerContext context) {
        super(context);
    }

    @Override
    public boolean handle(String type, String content) {
        warmConfiguredNodePath();
        switch (type) {
            case "get_dependency_status" -> handleGetStatus();
            case "install_dependency" -> handleInstall(content);
            case "uninstall_dependency" -> handleUninstall(content);
            case "update_dependency" -> handleInstall(content);
            case "check_dependency_updates" -> handleCheckUpdates(content);
            case "get_dependency_versions" -> handleGetVersions(content);
            case "check_node_environment" -> handleCheckNodeEnvironment();
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public List<String> getSupportedTypes() {
        return SUPPORTED_TYPES;
    }

    private void warmConfiguredNodePath() {
        String configured = settingsService.getNodePath();
        if (configured != null && !configured.isBlank()) {
            nodeDetector.verifyAndCacheNodePath(configured);
        }
    }

    private void handleGetStatus() {
        CompletableFuture.runAsync(
            () -> pushJavascript("window.updateDependencyStatus", dependencyManager.getAllSdkStatus()),
            AppExecutorUtil.getAppExecutorService()
        ).exceptionally(ex -> {
            LOG.warn("[DependencyHandler] dependency status request failed: " + ex.getMessage(), ex);
            pushJavascript("window.updateDependencyStatus", buildStatusErrorPayload(ex));
            return null;
        });
    }

    private void handleInstall(String content) {
        JsonObject request = gson.fromJson(content, JsonObject.class);
        String sdkId = request != null && request.has("id") ? request.get("id").getAsString() : "";
        String version = request != null && request.has("version") && !request.get("version").isJsonNull()
            ? request.get("version").getAsString() : null;

        CompletableFuture.runAsync(() -> {
            if (!dependencyManager.checkNodeEnvironment()) {
                JsonObject result = new JsonObject();
                result.addProperty("success", false);
                result.addProperty("sdkId", sdkId);
                result.addProperty("error", "node_not_configured");
                pushJavascript("window.dependencyInstallResult", result);
                return;
            }

            InstallResult installResult = dependencyManager.installSdkSync(sdkId, version, line -> {
                JsonObject progress = new JsonObject();
                progress.addProperty("sdkId", sdkId);
                progress.addProperty("log", line);
                pushJavascript("window.dependencyInstallProgress", progress);
            });

            pushJavascript("window.dependencyInstallResult", toJson(installResult));
            if (installResult.success()) {
                pushJavascript("window.updateDependencyStatus", dependencyManager.getAllSdkStatus());
            }
        }, AppExecutorUtil.getAppExecutorService()).exceptionally(ex -> {
            LOG.warn("[DependencyHandler] install failed: " + ex.getMessage(), ex);
            JsonObject result = new JsonObject();
            result.addProperty("success", false);
            result.addProperty("sdkId", sdkId);
            result.addProperty("error", ex.getMessage());
            pushJavascript("window.dependencyInstallResult", result);
            return null;
        });
    }

    private void handleUninstall(String content) {
        JsonObject request = gson.fromJson(content, JsonObject.class);
        String sdkId = request != null && request.has("id") ? request.get("id").getAsString() : "";

        CompletableFuture.runAsync(() -> {
            JsonObject result = new JsonObject();
            result.addProperty("sdkId", sdkId);
            try {
                result.addProperty("success", dependencyManager.uninstallSdk(sdkId));
            } catch (Exception e) {
                result.addProperty("success", false);
                result.addProperty("error", e.getMessage());
            }
            pushJavascript("window.dependencyUninstallResult", result);
            pushJavascript("window.updateDependencyStatus", dependencyManager.getAllSdkStatus());
        }, AppExecutorUtil.getAppExecutorService());
    }

    private void handleCheckUpdates(String content) {
        CompletableFuture.runAsync(() -> {
            JsonObject payload = new JsonObject();
            if (content != null && !content.isBlank()) {
                JsonObject request = gson.fromJson(content, JsonObject.class);
                if (request != null && request.has("id") && !request.get("id").isJsonNull()) {
                    String sdkId = request.get("id").getAsString();
                    payload.add(sdkId, toJson(dependencyManager.checkForUpdates(sdkId)));
                    pushJavascript("window.dependencyUpdateAvailable", payload);
                    return;
                }
            }
            for (SdkDefinition sdk : SdkDefinition.values()) {
                payload.add(sdk.getId(), toJson(dependencyManager.checkForUpdates(sdk.getId())));
            }
            pushJavascript("window.dependencyUpdateAvailable", payload);
        }, AppExecutorUtil.getAppExecutorService());
    }

    private void handleGetVersions(String content) {
        CompletableFuture.runAsync(() -> {
            JsonObject payload = new JsonObject();
            if (content != null && !content.isBlank()) {
                JsonObject request = gson.fromJson(content, JsonObject.class);
                if (request != null && request.has("id") && !request.get("id").isJsonNull()) {
                    String sdkId = request.get("id").getAsString();
                    payload.add(sdkId, buildVersionPayload(sdkId));
                    pushJavascript("window.dependencyVersionsLoaded", payload);
                    return;
                }
            }
            for (SdkDefinition sdk : SdkDefinition.values()) {
                payload.add(sdk.getId(), buildVersionPayload(sdk.getId()));
            }
            pushJavascript("window.dependencyVersionsLoaded", payload);
        }, AppExecutorUtil.getAppExecutorService());
    }

    private void handleCheckNodeEnvironment() {
        NodeDetectionResult result = nodeDetector.detectNodeWithDetails();
        JsonObject payload = new JsonObject();
        payload.addProperty("available", result.available());
        payload.addProperty("nodePath", result.nodePath());
        payload.addProperty("version", result.version());
        payload.addProperty("npmVersion", result.npmVersion());
        pushJavascript("window.nodeEnvironmentStatus", payload);
        pushJavascript("window.updateNodeEnvironment", payload);
    }

    private JsonObject buildVersionPayload(String sdkId) {
        JsonObject payload = new JsonObject();
        SdkDefinition sdk = SdkDefinition.fromId(sdkId);
        payload.addProperty("sdkId", sdkId);
        payload.add("versions", gson.toJsonTree(dependencyManager.getAvailableVersions(sdkId)));
        payload.add("fallbackVersions", gson.toJsonTree(dependencyManager.getFallbackVersions(sdkId)));
        payload.addProperty("source", "fallback");
        payload.addProperty("latestVersion", sdk != null ? sdk.getLockedVersion() : "");
        return payload;
    }

    private JsonObject toJson(InstallResult result) {
        return gson.toJsonTree(result).getAsJsonObject();
    }

    private JsonObject toJson(UpdateInfo result) {
        return gson.toJsonTree(result).getAsJsonObject();
    }

    private JsonObject buildStatusErrorPayload(Throwable throwable) {
        JsonObject payload = new JsonObject();
        String message = throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName();

        for (SdkDefinition sdk : SdkDefinition.values()) {
            JsonObject status = new JsonObject();
            status.addProperty("id", sdk.getId());
            status.addProperty("name", sdk.getDisplayName());
            status.addProperty("description", sdk.getDescription());
            status.addProperty("status", "error");
            status.addProperty("hasUpdate", false);
            status.addProperty("errorMessage", message);
            payload.add(sdk.getId(), status);
        }

        return payload;
    }

    private void pushJavascript(String functionName, JsonObject payload) {
        callJavaScript(functionName, gson.toJson(payload));
    }
}
