package com.protean.copilot.settings.manager;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
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

    public synchronized List<McpServerDefinition> listServers() {
        return new ArrayList<>(servers);
    }

    public synchronized void replaceServers(@NotNull List<McpServerDefinition> definitions) {
        servers.clear();
        servers.addAll(definitions);
    }
}
