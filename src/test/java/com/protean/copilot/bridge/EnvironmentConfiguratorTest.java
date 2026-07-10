package com.protean.copilot.bridge;

import com.protean.copilot.settings.CodemossSettingsService;
import org.junit.Test;

import java.nio.file.Files;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class EnvironmentConfiguratorTest {

    @Test
    public void configuresStableRuntimeEnvironment() throws Exception {
        CodemossSettingsService settings = new CodemossSettingsService(Files.createTempDirectory("env-config-"));
        EnvironmentConfigurator configurator = new EnvironmentConfigurator(settings);
        ProcessBuilder processBuilder = new ProcessBuilder("node", "--version");

        configurator.configureProcessEnvironment(processBuilder, "/usr/local/bin/node", Map.of("EXTRA_FLAG", "1"));

        Map<String, String> env = processBuilder.environment();
        assertTrue(env.get(detectPathKey(env)).contains("/usr/local/bin"));
        assertNotNull(env.get("HOME"));
        assertTrue(env.get("CODEX_HOME").endsWith(".codex"));
        assertTrue(env.get("CLAUDE_PERMISSION_DIR").contains("claude-permission"));
        assertEquals("1", env.get("EXTRA_FLAG"));
    }

    private static String detectPathKey(Map<String, String> env) {
        for (String key : env.keySet()) {
            if ("PATH".equalsIgnoreCase(key)) {
                return key;
            }
        }
        return "PATH";
    }
}
