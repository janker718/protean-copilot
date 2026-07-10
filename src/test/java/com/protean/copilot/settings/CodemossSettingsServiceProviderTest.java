package com.protean.copilot.settings;

import com.google.gson.JsonObject;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CodemossSettingsServiceProviderTest {

    @Test
    public void persistsClaudeProviderLifecycleAndOrder() throws Exception {
        Path configDirectory = Files.createTempDirectory("codemoss-providers-");
        CodemossSettingsService service = new CodemossSettingsService(configDirectory);

        service.addClaudeProvider(provider("claude-a", "Claude A"));
        service.addClaudeProvider(provider("claude-b", "Claude B"));
        service.switchClaudeProvider("claude-b");
        service.updateClaudeProvider("claude-b", provider("ignored", "Claude B Updated"));
        service.saveClaudeProviderOrder(List.of("claude-b", "claude-a"));

        List<JsonObject> providers = service.getClaudeProviders();
        assertEquals("claude-b", providers.get(0).get("id").getAsString());
        assertTrue(providers.get(0).get("isActive").getAsBoolean());
        assertEquals("Claude B Updated", providers.get(0).get("name").getAsString());

        service.deleteClaudeProvider("claude-b");
        assertEquals(3, service.getClaudeProviders().size());
        assertFalse(service.getClaudeProviders().get(0).get("isActive").getAsBoolean());
    }

    @Test
    public void persistsCodexProviderLifecycleAndCliLoginAuthorization() throws Exception {
        Path configDirectory = Files.createTempDirectory("codemoss-codex-providers-");
        CodemossSettingsService service = new CodemossSettingsService(configDirectory);

        service.addCodexProvider(provider("codex-a", "Codex A"));
        service.switchCodexProvider("codex-a");
        assertEquals("managed", service.getCodexRuntimeAccessMode());
        assertTrue(service.getCodexProviders().get(0).get("isActive").getAsBoolean());

        service.switchCodexProvider("__codex_cli_login__");
        assertEquals("cli_login", service.getCodexRuntimeAccessMode());
        service.setCodexLocalConfigAuthorized(false);
        assertEquals("inactive", service.getCodexRuntimeAccessMode());
    }

    private static JsonObject provider(String id, String name) {
        JsonObject provider = new JsonObject();
        provider.addProperty("id", id);
        provider.addProperty("name", name);
        return provider;
    }
}
