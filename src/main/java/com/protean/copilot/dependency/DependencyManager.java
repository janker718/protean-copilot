package com.protean.copilot.dependency;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;
import com.protean.copilot.bridge.NodeDetector;
import com.protean.copilot.provider.claude.NodeDetectionResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class DependencyManager {

    private static final Logger LOG = Logger.getInstance(DependencyManager.class);
    private static final Gson GSON = new Gson();

    private final NodeDetector nodeDetector;

    public DependencyManager(NodeDetector nodeDetector) {
        this.nodeDetector = nodeDetector;
    }

    public Path getDependenciesDir() {
        return Path.of(System.getProperty("user.home"), ".codemoss", "dependencies");
    }

    public Path getSdkDir(String sdkId) {
        return getDependenciesDir().resolve(sdkId);
    }

    public boolean checkNodeEnvironment() {
        return nodeDetector.detectNodeWithDetails().available();
    }

    public JsonObject getAllSdkStatus() {
        JsonObject result = new JsonObject();
        for (SdkDefinition sdk : SdkDefinition.values()) {
            JsonObject payload = new JsonObject();
            payload.addProperty("id", sdk.getId());
            payload.addProperty("name", sdk.getDisplayName());
            payload.addProperty("description", sdk.getDescription());
            payload.addProperty("installPath", getSdkDir(sdk.getId()).toString());

            String installedVersion = getInstalledVersion(sdk.getId());
            if (installedVersion != null) {
                payload.addProperty("status", "installed");
                payload.addProperty("installedVersion", installedVersion);
            } else {
                payload.addProperty("status", "not_installed");
            }

            payload.addProperty("latestVersion", sdk.getLockedVersion());
            payload.addProperty("hasUpdate", installedVersion != null && compareVersions(installedVersion, sdk.getLockedVersion()) < 0);
            result.add(sdk.getId(), payload);
        }
        return result;
    }

    public InstallResult installSdkSync(String sdkId, String requestedVersion, Consumer<String> logCallback) {
        SdkDefinition sdk = SdkDefinition.fromId(sdkId);
        if (sdk == null) {
            return InstallResult.failure(sdkId, requestedVersion, "Unknown SDK: " + sdkId, "");
        }

        NodeDetectionResult node = nodeDetector.detectNodeWithDetails();
        if (!node.available()) {
            return InstallResult.failure(sdkId, requestedVersion, "node_not_configured", "");
        }

        String resolvedVersion = sdk.getRequestedVersion(requestedVersion);
        Path sdkDir = getSdkDir(sdkId);
        StringBuilder logs = new StringBuilder();
        Consumer<String> log = line -> {
            logs.append(line).append('\n');
            if (logCallback != null) {
                logCallback.accept(line);
            }
        };

        try {
            Files.createDirectories(sdkDir);
            writePackageManifest(sdkDir, sdk);

            ArrayList<String> command = new ArrayList<>();
            command.add(nodeDetector.findNpmExecutable(node.nodePath()));
            command.add("install");
            command.add("--no-save");
            command.addAll(sdk.getAllPackages(resolvedVersion));

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(sdkDir.toFile());
            pb.redirectErrorStream(true);

            log.accept("Installing " + sdk.getDisplayName() + " " + resolvedVersion);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            )) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.accept(line);
                }
            }

            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                return InstallResult.failure(sdkId, resolvedVersion, "Installation timed out", logs.toString());
            }

            if (process.exitValue() != 0) {
                return InstallResult.failure(sdkId, resolvedVersion, "npm install failed", logs.toString());
            }

            String installedVersion = getInstalledVersion(sdkId);
            if (installedVersion == null) {
                installedVersion = resolvedVersion;
            }
            return InstallResult.success(sdkId, installedVersion, resolvedVersion, logs.toString());
        } catch (Exception e) {
            LOG.warn("[DependencyManager] Failed to install " + sdkId + ": " + e.getMessage(), e);
            return InstallResult.failure(sdkId, resolvedVersion, e.getMessage(), logs.toString());
        }
    }

    public boolean uninstallSdk(String sdkId) throws IOException {
        Path sdkDir = getSdkDir(sdkId);
        if (!Files.exists(sdkDir)) {
            return true;
        }
        try (var stream = Files.walk(sdkDir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
        return true;
    }

    public UpdateInfo checkForUpdates(String sdkId) {
        SdkDefinition sdk = SdkDefinition.fromId(sdkId);
        if (sdk == null) {
            return UpdateInfo.error(sdkId, sdkId, "Unknown SDK");
        }
        String installedVersion = getInstalledVersion(sdkId);
        if (installedVersion == null) {
            return UpdateInfo.error(sdkId, sdk.getDisplayName(), "SDK not installed");
        }
        if (compareVersions(installedVersion, sdk.getLockedVersion()) < 0) {
            return UpdateInfo.updateAvailable(sdkId, sdk.getDisplayName(), installedVersion, sdk.getLockedVersion());
        }
        return UpdateInfo.upToDate(sdkId, sdk.getDisplayName(), installedVersion);
    }

    public List<String> getAvailableVersions(String sdkId) {
        SdkDefinition sdk = SdkDefinition.fromId(sdkId);
        if (sdk == null) {
            return List.of();
        }
        return sdk.getFallbackVersions();
    }

    public List<String> getFallbackVersions(String sdkId) {
        SdkDefinition sdk = SdkDefinition.fromId(sdkId);
        if (sdk == null) {
            return List.of();
        }
        return sdk.getFallbackVersions();
    }

    public String getInstalledVersion(String sdkId) {
        SdkDefinition sdk = SdkDefinition.fromId(sdkId);
        if (sdk == null) {
            return null;
        }

        Path packageJson = resolvePackageJsonPath(sdkId, sdk.getNpmPackage());
        if (!Files.exists(packageJson)) {
            return null;
        }

        try (Reader reader = Files.newBufferedReader(packageJson, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (json.has("version") && !json.get("version").isJsonNull()) {
                return json.get("version").getAsString();
            }
        } catch (Exception e) {
            LOG.warn("[DependencyManager] Failed reading installed version for " + sdkId + ": " + e.getMessage());
        }
        return null;
    }

    private Path resolvePackageJsonPath(String sdkId, String npmPackage) {
        Path path = getSdkDir(sdkId).resolve("node_modules");
        for (String part : npmPackage.split("/")) {
            path = path.resolve(part);
        }
        return path.resolve("package.json");
    }

    private void writePackageManifest(Path sdkDir, SdkDefinition sdk) throws IOException {
        Path manifest = sdkDir.resolve("package.json");
        if (Files.exists(manifest)) {
            return;
        }
        JsonObject json = new JsonObject();
        json.addProperty("name", sdk.getId());
        json.addProperty("private", true);
        json.addProperty("description", sdk.getDescription());
        Files.writeString(manifest, GSON.toJson(json), StandardCharsets.UTF_8);
    }

    static int compareVersions(String left, String right) {
        String[] leftParts = left.split("\\.");
        String[] rightParts = right.split("\\.");
        int max = Math.max(leftParts.length, rightParts.length);
        for (int i = 0; i < max; i++) {
            int leftValue = i < leftParts.length ? parseVersionPart(leftParts[i]) : 0;
            int rightValue = i < rightParts.length ? parseVersionPart(rightParts[i]) : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return 0;
    }

    private static int parseVersionPart(String part) {
        try {
            return Integer.parseInt(part.replaceAll("[^0-9].*$", ""));
        } catch (Exception ignored) {
            return 0;
        }
    }
}
