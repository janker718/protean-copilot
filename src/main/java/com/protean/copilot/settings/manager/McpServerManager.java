package com.protean.copilot.settings.manager;

import com.google.gson.JsonObject;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.protean.copilot.settings.CodemossSettingsService;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@Service(Service.Level.PROJECT)
public final class McpServerManager {

    public record McpServerDefinition(String name, String command, List<String> args) {
    }

    public static McpServerManager getInstance(@NotNull Project project) {
        return project.getService(McpServerManager.class);
    }

    private final List<McpServerDefinition> servers = new ArrayList<>();

    public synchronized void reloadFromSettings(@NotNull CodemossSettingsService settings, boolean codexScope) {
        servers.clear();
        try {
            for (JsonObject server : settings.getMcpServers(codexScope)) {
                if (server.has("enabled") && !server.get("enabled").isJsonNull() && !server.get("enabled").getAsBoolean()) {
                    continue;
                }
                if (!server.has("server") || !server.get("server").isJsonObject()) {
                    continue;
                }
                JsonObject spec = server.getAsJsonObject("server");
                String name = server.has("name") && !server.get("name").isJsonNull()
                    ? server.get("name").getAsString()
                    : server.get("id").getAsString();
                String command = spec.has("command") && !spec.get("command").isJsonNull()
                    ? spec.get("command").getAsString()
                    : spec.has("url") && !spec.get("url").isJsonNull()
                        ? spec.get("url").getAsString()
                        : "";
                List<String> args = new ArrayList<>();
                if (spec.has("args") && spec.get("args").isJsonArray()) {
                    spec.getAsJsonArray("args").forEach(element -> args.add(element.getAsString()));
                }
                servers.add(new McpServerDefinition(name, command, args));
            }
        } catch (Exception ignored) {
            // Settings failures should not break the settings page.
        }
    }

    public synchronized List<McpServerDefinition> listServers() {
        return new ArrayList<>(servers);
    }

    public synchronized void replaceServers(@NotNull List<McpServerDefinition> definitions) {
        servers.clear();
        servers.addAll(definitions);
    }
}
