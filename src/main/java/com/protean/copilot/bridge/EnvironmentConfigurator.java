package com.protean.copilot.bridge;

import com.intellij.openapi.diagnostic.Logger;
import com.protean.copilot.settings.CodemossSettingsService;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configures process environments for Node-based bridge and dependency commands.
 */
public final class EnvironmentConfigurator {

    private static final Logger LOG = Logger.getInstance(EnvironmentConfigurator.class);
    private static final String CLAUDE_PERMISSION_DIR_ENV = "CLAUDE_PERMISSION_DIR";
    private static final String CLAUDE_PERMISSION_SAFETY_NET_ENV = "CLAUDE_PERMISSION_SAFETY_NET_MS";
    private static final String CODEX_HOME_ENV = "CODEX_HOME";

    private final CodemossSettingsService settingsService;

    public EnvironmentConfigurator() {
        this(new CodemossSettingsService());
    }

    EnvironmentConfigurator(CodemossSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    public void configureProcessEnvironment(ProcessBuilder processBuilder, String nodeExecutable, Map<String, String> extraEnv) {
        Map<String, String> env = processBuilder.environment();
        String pathKey = detectPathKey(env);
        env.put(pathKey, mergedPath(env.get(pathKey), nodeExecutable));

        String home = env.get("HOME");
        if (home == null || home.isBlank()) {
            home = System.getProperty("user.home");
            env.put("HOME", home);
        }

        String codexHome = env.get(CODEX_HOME_ENV);
        if (codexHome == null || codexHome.isBlank()) {
            env.put(CODEX_HOME_ENV, Path.of(home, ".codex").toString());
        }

        String permissionDir = env.get(CLAUDE_PERMISSION_DIR_ENV);
        if (permissionDir == null || permissionDir.isBlank()) {
            env.put(CLAUDE_PERMISSION_DIR_ENV, Path.of(System.getProperty("java.io.tmpdir"), "claude-permission").toString());
        }

        if (!env.containsKey(CLAUDE_PERMISSION_SAFETY_NET_ENV)) {
            env.put(CLAUDE_PERMISSION_SAFETY_NET_ENV, String.valueOf(permissionSafetyNetMs()));
        }

        if (extraEnv != null) {
            extraEnv.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                    env.put(key, value);
                }
            });
        }
    }

    private long permissionSafetyNetMs() {
        try {
            long timeoutSeconds = settingsService.getPermissionDialogTimeoutSeconds();
            return (timeoutSeconds + CodemossSettingsService.PERMISSION_SAFETY_NET_BUFFER_SECONDS) * 1000L;
        } catch (Exception e) {
            LOG.warn("Failed to read permission dialog timeout: " + e.getMessage());
            return (CodemossSettingsService.DEFAULT_PERMISSION_DIALOG_TIMEOUT_SECONDS
                + CodemossSettingsService.PERMISSION_SAFETY_NET_BUFFER_SECONDS) * 1000L;
        }
    }

    private static String detectPathKey(Map<String, String> env) {
        for (String key : env.keySet()) {
            if ("PATH".equalsIgnoreCase(key)) {
                return key;
            }
        }
        return isWindows() ? "Path" : "PATH";
    }

    private static String mergedPath(String currentPath, String nodeExecutable) {
        Map<String, Boolean> orderedEntries = new LinkedHashMap<>();
        if (nodeExecutable != null && !nodeExecutable.isBlank()) {
            File nodeFile = new File(nodeExecutable);
            String parent = nodeFile.getParent();
            if (parent != null && !parent.isBlank()) {
                orderedEntries.put(parent, true);
            }
        }
        if (currentPath != null && !currentPath.isBlank()) {
            for (String entry : currentPath.split(File.pathSeparator)) {
                if (!entry.isBlank()) {
                    orderedEntries.putIfAbsent(entry, true);
                }
            }
        }
        return String.join(File.pathSeparator, orderedEntries.keySet());
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
