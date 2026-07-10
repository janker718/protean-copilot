package com.protean.copilot.handler;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.protean.copilot.handler.core.BaseMessageHandler;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.settings.CodemossSettingsService;
import com.protean.copilot.settings.manager.McpServerManager;

import java.io.File;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Handles WebView actions which are not part of a session, permission, or diff flow.
 * Keeping this protocol boundary explicit prevents settings pages from silently timing out.
 */
public final class FrontendActionCoverageHandler extends BaseMessageHandler {

    private static final long MAX_IMPORT_FILE_SIZE_BYTES = 5L * 1024L * 1024L;
    private static final DateTimeFormatter EXPORT_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZoneOffset.UTC);

    private static final Logger LOG = Logger.getInstance(FrontendActionCoverageHandler.class);
    private static final List<String> TYPES = List.of(
        "read_clipboard", "write_clipboard", "get_agents", "add_agent", "update_agent", "delete_agent",
        "get_selected_agent", "set_selected_agent", "get_prompts", "add_prompt", "update_prompt", "delete_prompt",
        "get_project_info", "get_mcp_servers", "get_mcp_server_status", "get_mcp_server_tools",
        "add_mcp_server", "update_mcp_server", "delete_mcp_server", "toggle_mcp_server",
        "get_codex_mcp_servers", "get_codex_mcp_server_status", "get_codex_mcp_server_tools",
        "add_codex_mcp_server", "update_codex_mcp_server", "delete_codex_mcp_server", "toggle_codex_mcp_server",
        "get_all_skills", "import_skill", "open_skill", "delete_skill", "toggle_skill",
        "get_linkify_capabilities",
        "list_files", "refresh_file", "rewind_files", "undo_all_file_changes", "save_json",
        "open_file_chooser_for_cc_switch", "preview_cc_switch_import", "save_imported_providers",
        "export_agents", "import_agents_file", "save_imported_agents", "export_prompts", "import_prompts_file", "save_imported_prompts"
    );

    private final Gson gson = new Gson();
    private final CodemossSettingsService settings;

    public FrontendActionCoverageHandler(HandlerContext context) {
        this(context, new CodemossSettingsService());
    }

    FrontendActionCoverageHandler(HandlerContext context, CodemossSettingsService settings) {
        super(context);
        this.settings = settings;
    }

    @Override public List<String> getSupportedTypes() { return TYPES; }

    @Override public boolean handle(String type, String content) {
        if (!TYPES.contains(type)) return false;
        try {
            switch (type) {
                case "read_clipboard" -> readClipboard();
                case "write_clipboard" -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(content == null ? "" : content), null);
                case "get_agents" -> callback("window.updateAgents", gson.toJson(settings.getAgents()));
                case "add_agent" -> { settings.addAgent(object(content)); agentResult("add", true, null); }
                case "update_agent" -> { JsonObject data = object(content); settings.updateAgent(string(data, "id"), data.getAsJsonObject("updates")); agentResult("update", true, null); }
                case "delete_agent" -> { settings.deleteAgent(string(object(content), "id")); agentResult("delete", true, null); }
                case "get_selected_agent" -> selectedAgent();
                case "set_selected_agent" -> settings.setSelectedAgentId(string(object(content), "id"));
                case "get_prompts" -> callback(isProject(content) ? "window.updateProjectPrompts" : "window.updateGlobalPrompts",
                    gson.toJson(settings.getPrompts(isProject(content))));
                case "add_prompt" -> addPrompt(content);
                case "update_prompt" -> updatePrompt(content);
                case "delete_prompt" -> deletePrompt(content);
                case "get_project_info" -> projectInfo();
                case "get_mcp_servers" -> mcpServers("window.updateMcpServers");
                case "get_codex_mcp_servers" -> mcpServers("window.updateCodexMcpServers");
                case "get_mcp_server_status" -> mcpStatus("window.updateMcpServerStatus");
                case "get_codex_mcp_server_status" -> mcpStatus("window.updateCodexMcpServerStatus");
                case "get_mcp_server_tools", "get_codex_mcp_server_tools" -> callback("window.updateMcpServerTools", toolResult(content));
                case "add_mcp_server" -> addMcpServer(content, false);
                case "update_mcp_server" -> updateMcpServer(content, false);
                case "delete_mcp_server" -> deleteMcpServer(content, false);
                case "toggle_mcp_server" -> toggleMcpServer(content, false);
                case "add_codex_mcp_server" -> addMcpServer(content, true);
                case "update_codex_mcp_server" -> updateMcpServer(content, true);
                case "delete_codex_mcp_server" -> deleteMcpServer(content, true);
                case "toggle_codex_mcp_server" -> toggleMcpServer(content, true);
                case "get_all_skills" -> skills();
                case "import_skill" -> importSkill(content);
                case "open_skill" -> openSkill(content);
                case "delete_skill" -> deleteSkill(content);
                case "toggle_skill" -> toggleSkill(content);
                case "get_linkify_capabilities" -> callback("window.updateLinkifyCapabilities", "{\"fileNavigationEnabled\":true,\"classNavigationEnabled\":false}");
                case "list_files" -> listFiles(content);
                case "undo_all_file_changes" -> callback("window.onUndoAllFileResult", "{\"success\":false,\"error\":\"Batch undo is not available for this session\"}");
                case "export_agents" -> exportAgents(content);
                case "import_agents_file" -> importAgentsFile();
                case "save_imported_agents" -> saveImportedAgents(content);
                case "export_prompts" -> exportPrompts(content);
                case "import_prompts_file" -> importPromptsFile(content);
                case "save_imported_prompts" -> saveImportedPrompts(content);
                case "refresh_file", "rewind_files", "save_json", "open_file_chooser_for_cc_switch", "preview_cc_switch_import",
                     "save_imported_providers" ->
                    callback("window.showError", "This action is not available in the current runtime.");
                default -> { }
            }
        } catch (Exception e) {
            LOG.warn("Frontend action failed: " + type, e);
            callback("window.showError", e.getMessage() == null ? "Action failed" : e.getMessage());
        }
        return true;
    }

    private JsonObject object(String content) { return JsonParser.parseString(content == null || content.isBlank() ? "{}" : content).getAsJsonObject(); }
    private String string(JsonObject object, String name) { return object.has(name) ? object.get(name).getAsString() : null; }
    private boolean isProject(String content) { return object(content).has("scope") && "project".equals(string(object(content), "scope")); }
    // ProteanChatWindow escapes JavaScript arguments centrally. Escaping here
    // would turn JSON into a doubly-escaped string that the WebView cannot parse.
    private void callback(String name, String json) { callJavaScript(name, json); }
    private void selectedAgent() throws Exception {
        String selectedAgentId = settings.getSelectedAgentId();
        callback("window.updateSelectedAgent", gson.toJson(settings.getAgent(selectedAgentId)));
    }
    private void agentResult(String operation, boolean success, String error) { JsonObject result = new JsonObject(); result.addProperty("operation", operation); result.addProperty("success", success); if (error != null) result.addProperty("error", error); callback("window.agentOperationResult", gson.toJson(result)); callback("window.updateAgents", gson.toJson(safeAgents())); }
    private List<JsonObject> safeAgents() { try { return settings.getAgents(); } catch (Exception ignored) { return List.of(); } }
    private void projectInfo() { JsonObject value = new JsonObject(); boolean available = context.getProject() != null && !context.getProject().isDisposed() && context.getProject().getBasePath() != null; value.addProperty("available", available); if (available) { value.addProperty("name", context.getProject().getName()); value.addProperty("path", context.getProject().getBasePath()); } callback("window.updateProjectInfo", gson.toJson(value)); }
    private void readClipboard() throws Exception { Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor); callback("window.onClipboardRead", data == null ? "" : data.toString()); }

    private void mcpServers(String callbackName) {
        JsonArray servers = new JsonArray();
        boolean codexScope = callbackName.contains("Codex");
        try {
            List<JsonObject> configured = settings.getMcpServers(codexScope);
            if (context.getProject() != null && !context.getProject().isDisposed()) {
                McpServerManager.getInstance(context.getProject()).reloadFromSettings(settings, codexScope);
            }
            configured.forEach(server -> servers.add(server.deepCopy()));
        } catch (Exception exception) {
            LOG.warn("Unable to load MCP servers", exception);
        }
        callback(callbackName, gson.toJson(servers));
    }

    private void mcpStatus(String callbackName) {
        JsonArray statuses = new JsonArray();
        try {
            boolean codexScope = callbackName.contains("Codex");
            for (JsonObject definition : settings.getMcpServers(codexScope)) {
                JsonObject status = new JsonObject();
                status.addProperty("name", string(definition, "id"));
                boolean enabled = !definition.has("enabled") || definition.get("enabled").isJsonNull() || definition.get("enabled").getAsBoolean();
                status.addProperty("status", enabled ? "pending" : "failed");
                statuses.add(status);
            }
        } catch (Exception exception) {
            LOG.warn("Unable to load MCP status", exception);
        }
        callback(callbackName, gson.toJson(statuses));
    }

    private void addPrompt(String content) throws Exception {
        JsonObject request = object(content);
        settings.addPrompt(isProject(content), request.getAsJsonObject("prompt"));
        promptResult("add", true, null, isProject(content));
    }

    private void updatePrompt(String content) throws Exception {
        JsonObject request = object(content);
        settings.updatePrompt(isProject(content), string(request, "id"), request.getAsJsonObject("updates"));
        promptResult("update", true, null, isProject(content));
    }

    private void deletePrompt(String content) throws Exception {
        JsonObject request = object(content);
        settings.deletePrompt(isProject(content), string(request, "id"));
        promptResult("delete", true, null, isProject(content));
    }

    private void exportPrompts(String content) throws Exception {
        JsonObject request = object(content);
        boolean projectScope = "project".equals(string(request, "scope"));
        List<String> selectedIds = stringList(request, "promptIds");
        List<JsonObject> prompts = filterByIds(settings.getPrompts(projectScope), selectedIds);

        JsonObject export = new JsonObject();
        export.addProperty("format", "claude-code-prompts-export-v1");
        export.addProperty("scope", projectScope ? "project" : "global");
        export.addProperty("exportedAt", Instant.now().toString());
        JsonArray items = new JsonArray();
        prompts.forEach(prompt -> items.add(prompt.deepCopy()));
        export.add("prompts", items);

        Path output = writeExportFile(projectScope ? "project-prompts" : "global-prompts", export);
        callback("window.showSuccess", "Exported prompts to " + output);
    }

    private void importPromptsFile(String content) {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                JsonObject request = object(content);
                boolean projectScope = "project".equals(string(request, "scope"));
                VirtualFile selectedFile = chooseSingleJsonFile("Import Prompts", "Select a prompts export JSON file");
                if (selectedFile == null) {
                    return;
                }
                JsonObject importData = readJsonFile(selectedFile);
                String format = string(importData, "format");
                if (format == null || !format.startsWith("claude-code-prompts-export-v")) {
                    throw new IllegalArgumentException("Invalid prompt export file format");
                }
                if (!importData.has("prompts") || !importData.get("prompts").isJsonArray()) {
                    throw new IllegalArgumentException("Prompt export file is missing prompts");
                }

                List<JsonObject> existing = settings.getPrompts(projectScope);
                JsonObject preview = buildImportPreview(importData.getAsJsonArray("prompts"), existing);
                callback("window.promptImportPreviewResult", gson.toJson(preview));
            } catch (Exception e) {
                LOG.warn("Prompt import preview failed", e);
                callback("window.showError", e.getMessage() == null ? "Failed to import prompts" : e.getMessage());
            }
        });
    }

    private void saveImportedPrompts(String content) throws Exception {
        JsonObject request = object(content);
        boolean projectScope = "project".equals(string(request, "scope"));
        JsonArray prompts = request.has("prompts") && request.get("prompts").isJsonArray()
            ? request.getAsJsonArray("prompts")
            : new JsonArray();
        String strategy = string(request, "strategy");
        JsonObject result = applyImport(itemsFromArray(prompts), settings.getPrompts(projectScope), strategy,
            item -> settings.addPrompt(projectScope, item),
            item -> settings.updatePrompt(projectScope, string(item, "id"), item));
        result.addProperty("scope", projectScope ? "project" : "global");
        callback("window.promptImportResult", gson.toJson(result));
        callback(projectScope ? "window.updateProjectPrompts" : "window.updateGlobalPrompts",
            gson.toJson(settings.getPrompts(projectScope)));
    }

    private void exportAgents(String content) throws Exception {
        JsonObject request = object(content);
        List<String> selectedIds = stringList(request, "agentIds");
        List<JsonObject> agents = filterByIds(settings.getAgents(), selectedIds);

        JsonObject export = new JsonObject();
        export.addProperty("format", "claude-code-agents-export-v1");
        export.addProperty("exportedAt", Instant.now().toString());
        JsonArray items = new JsonArray();
        agents.forEach(agent -> items.add(agent.deepCopy()));
        export.add("agents", items);

        Path output = writeExportFile("agents", export);
        callback("window.showSuccess", "Exported agents to " + output);
    }

    private void importAgentsFile() {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                VirtualFile selectedFile = chooseSingleJsonFile("Import Agents", "Select an agents export JSON file");
                if (selectedFile == null) {
                    return;
                }
                JsonObject importData = readJsonFile(selectedFile);
                if (!"claude-code-agents-export-v1".equals(string(importData, "format"))) {
                    throw new IllegalArgumentException("Invalid agent export file format");
                }
                if (!importData.has("agents") || !importData.get("agents").isJsonArray()) {
                    throw new IllegalArgumentException("Agent export file is missing agents");
                }

                JsonObject preview = buildImportPreview(importData.getAsJsonArray("agents"), settings.getAgents());
                callback("window.agentImportPreviewResult", gson.toJson(preview));
            } catch (Exception e) {
                LOG.warn("Agent import preview failed", e);
                callback("window.showError", e.getMessage() == null ? "Failed to import agents" : e.getMessage());
            }
        });
    }

    private void saveImportedAgents(String content) throws Exception {
        JsonObject request = object(content);
        JsonArray agents = request.has("agents") && request.get("agents").isJsonArray()
            ? request.getAsJsonArray("agents")
            : new JsonArray();
        String strategy = string(request, "strategy");
        JsonObject result = applyImport(itemsFromArray(agents), settings.getAgents(), strategy,
            settings::addAgent,
            item -> settings.updateAgent(string(item, "id"), item));
        callback("window.agentImportResult", gson.toJson(result));
        callback("window.updateAgents", gson.toJson(settings.getAgents()));
    }

    private void promptResult(String operation, boolean success, String error, boolean projectScope) throws Exception {
        JsonObject result = new JsonObject();
        result.addProperty("operation", operation);
        result.addProperty("success", success);
        if (error != null) {
            result.addProperty("error", error);
        }
        callback("window.promptOperationResult", gson.toJson(result));
        callback(projectScope ? "window.updateProjectPrompts" : "window.updateGlobalPrompts",
            gson.toJson(settings.getPrompts(projectScope)));
    }

    private void addMcpServer(String content, boolean codexScope) throws Exception {
        JsonObject server = object(content);
        settings.addMcpServer(codexScope, server);
        reloadMcpManager(codexScope);
        mcpServers(codexScope ? "window.updateCodexMcpServers" : "window.updateMcpServers");
    }

    private void updateMcpServer(String content, boolean codexScope) throws Exception {
        JsonObject request = object(content);
        String id = string(request, "id");
        settings.updateMcpServer(codexScope, id, request);
        reloadMcpManager(codexScope);
        mcpServers(codexScope ? "window.updateCodexMcpServers" : "window.updateMcpServers");
    }

    private void deleteMcpServer(String content, boolean codexScope) throws Exception {
        JsonObject request = object(content);
        settings.deleteMcpServer(codexScope, string(request, "id"));
        reloadMcpManager(codexScope);
        mcpServers(codexScope ? "window.updateCodexMcpServers" : "window.updateMcpServers");
    }

    private void toggleMcpServer(String content, boolean codexScope) throws Exception {
        JsonObject request = object(content);
        settings.setMcpServerEnabled(codexScope, string(request, "id"), request.has("enabled") && request.get("enabled").getAsBoolean());
        reloadMcpManager(codexScope);
        mcpServers(codexScope ? "window.updateCodexMcpServers" : "window.updateMcpServers");
        mcpStatus(codexScope ? "window.updateCodexMcpServerStatus" : "window.updateMcpServerStatus");
    }

    private void reloadMcpManager(boolean codexScope) {
        if (context.getProject() == null || context.getProject().isDisposed()) {
            return;
        }
        McpServerManager.getInstance(context.getProject()).reloadFromSettings(settings, codexScope);
    }

    private String toolResult(String content) {
        JsonObject request = object(content);
        String serverId = string(request, "serverId");
        JsonObject result = new JsonObject();
        JsonArray tools = new JsonArray();
        if (serverId != null && !serverId.isBlank()) {
            JsonObject tool = new JsonObject();
            tool.addProperty("name", serverId + ".status");
            tool.addProperty("description", "Placeholder MCP tool metadata for configured server.");
            JsonObject schema = new JsonObject();
            schema.addProperty("type", "object");
            schema.add("properties", new JsonObject());
            tool.add("inputSchema", schema);
            tools.add(tool);
        }
        result.add("tools", tools);
        return gson.toJson(result);
    }

    private void openSkill(String content) {
        if (context.getProject() == null || context.getProject().isDisposed()) {
            throw new IllegalStateException("Project is unavailable");
        }
        String path = string(object(content), "path");
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Skill path is required");
        }
        VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.replace('\\', '/'));
        if (file == null) {
            throw new IllegalArgumentException("Skill file not found: " + path);
        }
        ApplicationManager.getApplication().invokeLater(() -> FileEditorManager.getInstance(context.getProject()).openFile(file, true));
    }

    private void importSkill(String content) {
        ApplicationManager.getApplication().invokeLater(() -> {
            try {
                JsonObject request = object(content);
                String scope = string(request, "scope");
                if (scope == null || scope.isBlank()) {
                    throw new IllegalArgumentException("Skill scope is required");
                }
                boolean codexScope = "codex".equalsIgnoreCase(context.getCurrentProvider());
                FileChooserDescriptor descriptor = codexScope
                    ? new FileChooserDescriptor(false, true, false, false, false, true)
                    : new FileChooserDescriptor(true, true, false, false, false, true);
                descriptor.setTitle(codexScope ? "Select Codex Skill Folder" : "Select Skill Files or Folders");

                VirtualFile initialDir = initialChooserDirectory();
                VirtualFile[] selectedFiles = FileChooser.chooseFiles(descriptor, context.getProject(), initialDir);
                if (selectedFiles.length == 0) {
                    return;
                }

                Path base = skillBase(scope, true);
                Files.createDirectories(base);
                int imported = 0;
                List<String> errors = new ArrayList<>();
                for (VirtualFile file : selectedFiles) {
                    try {
                        copySkillIntoScope(Paths.get(file.getPath()), base, codexScope);
                        imported++;
                    } catch (Exception copyError) {
                        errors.add(copyError.getMessage() == null ? file.getPath() : copyError.getMessage());
                    }
                }

                JsonObject result = new JsonObject();
                result.addProperty("success", imported > 0 || errors.isEmpty());
                result.addProperty("count", imported);
                result.addProperty("total", selectedFiles.length);
                if (!errors.isEmpty()) {
                    JsonArray errorArray = new JsonArray();
                    errors.forEach(errorArray::add);
                    result.add("errors", errorArray);
                    if (imported == 0) {
                        result.addProperty("success", false);
                        result.addProperty("error", errors.get(0));
                    }
                }
                callback("window.skillImportResult", gson.toJson(result));
                if (imported > 0) {
                    skills();
                }
            } catch (Exception e) {
                LOG.warn("Skill import failed", e);
                JsonObject result = new JsonObject();
                result.addProperty("success", false);
                result.addProperty("error", e.getMessage() == null ? "Failed to import skill" : e.getMessage());
                callback("window.skillImportResult", gson.toJson(result));
            }
        });
    }

    private void deleteSkill(String content) throws IOException {
        JsonObject request = object(content);
        Path path = Paths.get(requiredSkillPath(request));
        boolean success = deletePath(path);
        JsonObject result = new JsonObject();
        result.addProperty("success", success);
        if (!success) {
            result.addProperty("error", "Skill does not exist: " + path);
        }
        callback("window.skillDeleteResult", gson.toJson(result));
        skills();
    }

    private void toggleSkill(String content) throws IOException {
        JsonObject request = object(content);
        Path source = Paths.get(requiredSkillPath(request));
        boolean enabled = request.has("enabled") && request.get("enabled").getAsBoolean();
        Path target = toggleTarget(source, enabled);
        JsonObject result = new JsonObject();
        result.addProperty("name", source.getFileName().toString().replaceFirst("\\.md$", ""));
        if (!Files.exists(source)) {
            result.addProperty("success", false);
            result.addProperty("error", "Skill path does not exist: " + source);
            callback("window.skillToggleResult", gson.toJson(result));
            return;
        }
        Files.createDirectories(target.getParent());
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        result.addProperty("success", true);
        result.addProperty("enabled", !enabled);
        callback("window.skillToggleResult", gson.toJson(result));
        skills();
    }

    private String requiredSkillPath(JsonObject request) {
        String path = string(request, "path");
        if (path != null && !path.isBlank()) {
            return path;
        }
        String scope = string(request, "scope");
        String name = string(request, "name");
        if (scope == null || name == null) {
            throw new IllegalArgumentException("Skill path is required");
        }
        return skillBase(scope, true).resolve(name.endsWith(".md") ? name : name + ".md").toString();
    }

    private Path toggleTarget(Path source, boolean currentlyEnabled) {
        String path = source.toString();
        if (path.contains("/.claude/skills") || path.contains("/.agents/skills")) {
            String replaced = path.replace("/.claude/skills", "/.codemoss/skills-disabled")
                .replace("/.agents/skills", "/.codemoss/skills-disabled");
            return currentlyEnabled ? Paths.get(replaced) : source;
        }
        if (path.contains("/.codemoss/skills-disabled")) {
            String restored = path.contains("/.agents/")
                ? path.replace("/.codemoss/skills-disabled", "/.agents/skills")
                : path.replace("/.codemoss/skills-disabled", "/.claude/skills");
            return currentlyEnabled ? source : Paths.get(restored);
        }
        return source;
    }

    private boolean deletePath(Path path) throws IOException {
        if (!Files.exists(path)) {
            return false;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(candidate -> {
                try {
                    Files.deleteIfExists(candidate);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
        } catch (RuntimeException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
        return true;
    }

    private void listFiles(String content) {
        JsonArray files = new JsonArray();
        if (context.getProject() == null || context.getProject().isDisposed() || context.getProject().getBasePath() == null) {
            callback("window.onFileListResult", "{\"files\":[]}");
            return;
        }
        JsonObject request = object(content);
        String query = string(request, "query");
        Path root = Paths.get(context.getProject().getBasePath()).normalize();
        try (Stream<Path> paths = Files.walk(root, 4)) {
            paths.filter(path -> !path.equals(root))
                .filter(path -> !isIgnored(path, root))
                .filter(path -> query == null || query.isBlank() || path.getFileName().toString().toLowerCase().contains(query.toLowerCase()))
                .sorted(Comparator.comparing(Path::toString))
                .limit(300)
                .forEach(path -> files.add(fileItem(path, root)));
        } catch (Exception exception) {
            LOG.warn("Unable to list project files", exception);
        }
        JsonObject result = new JsonObject();
        result.add("files", files);
        callback("window.onFileListResult", gson.toJson(result));
    }

    private static boolean isIgnored(Path path, Path root) {
        for (Path part : root.relativize(path)) {
            String name = part.toString();
            if (".git".equals(name) || ".idea".equals(name) || "node_modules".equals(name) || "build".equals(name) || "dist".equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static JsonObject fileItem(Path path, Path root) {
        JsonObject item = new JsonObject();
        String name = path.getFileName().toString();
        item.addProperty("name", name);
        item.addProperty("path", root.relativize(path).toString());
        item.addProperty("absolutePath", path.toString());
        boolean directory = Files.isDirectory(path);
        item.addProperty("type", directory ? "directory" : "file");
        if (!directory && name.lastIndexOf('.') >= 0) {
            item.addProperty("extension", name.substring(name.lastIndexOf('.') + 1));
        }
        return item;
    }

    private void skills() {
        JsonObject result = new JsonObject();
        result.add("global", skillMap(Paths.get(System.getProperty("user.home"), ".claude", "skills"), "global"));
        result.add("local", projectSkills(".claude", "skills", "local"));
        result.add("user", skillMap(Paths.get(System.getProperty("user.home"), ".agents", "skills"), "user"));
        result.add("repo", projectSkills(".agents", "skills", "repo"));
        callback("window.updateSkills", gson.toJson(result));
    }

    private VirtualFile chooseSingleJsonFile(String title, String description) {
        FileChooserDescriptor descriptor = new FileChooserDescriptor(true, false, false, false, false, false);
        descriptor.setTitle(title);
        descriptor.setDescription(description);
        descriptor.withFileFilter(file -> {
            String extension = file.getExtension();
            return extension != null && extension.equalsIgnoreCase("json");
        });
        VirtualFile[] selectedFiles = FileChooser.chooseFiles(descriptor, context.getProject(), initialChooserDirectory());
        return selectedFiles.length == 0 ? null : selectedFiles[0];
    }

    private VirtualFile initialChooserDirectory() {
        if (context.getProject() == null || context.getProject().isDisposed() || context.getProject().getBasePath() == null) {
            return null;
        }
        return LocalFileSystem.getInstance().findFileByPath(context.getProject().getBasePath().replace('\\', '/'));
    }

    private JsonObject readJsonFile(VirtualFile file) throws IOException {
        File ioFile = new File(file.getPath());
        if (!ioFile.exists() || !ioFile.canRead()) {
            throw new IOException("Cannot read file: " + ioFile.getAbsolutePath());
        }
        if (ioFile.length() > MAX_IMPORT_FILE_SIZE_BYTES) {
            throw new IOException("File size exceeds 5MB limit");
        }
        return JsonParser.parseString(Files.readString(ioFile.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private JsonObject buildImportPreview(JsonArray array, List<JsonObject> existing) {
        java.util.Set<String> existingIds = new java.util.HashSet<>();
        existing.stream().map(item -> string(item, "id")).filter(id -> id != null && !id.isBlank()).forEach(existingIds::add);

        JsonArray items = new JsonArray();
        int newCount = 0;
        int updateCount = 0;
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject data = element.getAsJsonObject().deepCopy();
            String id = string(data, "id");
            if (id == null || id.isBlank()) {
                continue;
            }
            boolean conflict = existingIds.contains(id);
            JsonObject item = new JsonObject();
            item.add("data", data);
            item.addProperty("status", conflict ? "update" : "new");
            item.addProperty("conflict", conflict);
            items.add(item);
            if (conflict) {
                updateCount++;
            } else {
                newCount++;
            }
        }

        JsonObject summary = new JsonObject();
        summary.addProperty("total", items.size());
        summary.addProperty("newCount", newCount);
        summary.addProperty("updateCount", updateCount);

        JsonObject result = new JsonObject();
        result.add("items", items);
        result.add("summary", summary);
        return result;
    }

    private List<JsonObject> itemsFromArray(JsonArray array) {
        List<JsonObject> items = new ArrayList<>();
        for (JsonElement element : array) {
            if (element.isJsonObject()) {
                items.add(element.getAsJsonObject().deepCopy());
            }
        }
        return items;
    }

    private interface ImportAdder {
        void apply(JsonObject item) throws Exception;
    }

    private interface ImportUpdater {
        void apply(JsonObject item) throws Exception;
    }

    private JsonObject applyImport(List<JsonObject> items, List<JsonObject> existing, String strategy,
                                   ImportAdder add, ImportUpdater update) throws Exception {
        java.util.Set<String> existingIds = new java.util.HashSet<>();
        existing.stream().map(item -> string(item, "id")).filter(id -> id != null && !id.isBlank()).forEach(existingIds::add);

        int imported = 0;
        int updated = 0;
        int skipped = 0;
        for (JsonObject item : items) {
            String id = string(item, "id");
            if (id == null || id.isBlank()) {
                skipped++;
                continue;
            }
            boolean conflict = existingIds.contains(id);
            if (!conflict) {
                add.apply(item.deepCopy());
                existingIds.add(id);
                imported++;
                continue;
            }

            String resolvedStrategy = strategy == null || strategy.isBlank() ? "skip" : strategy;
            switch (resolvedStrategy) {
                case "overwrite" -> {
                    update.apply(item.deepCopy());
                    updated++;
                }
                case "duplicate" -> {
                    JsonObject duplicate = item.deepCopy();
                    String duplicateId = uniqueDuplicateId(id, existingIds);
                    duplicate.addProperty("id", duplicateId);
                    if (duplicate.has("name") && !duplicate.get("name").isJsonNull()) {
                        duplicate.addProperty("name", duplicate.get("name").getAsString() + " Copy");
                    }
                    add.apply(duplicate);
                    existingIds.add(duplicateId);
                    imported++;
                }
                default -> skipped++;
            }
        }

        JsonObject result = new JsonObject();
        result.addProperty("success", true);
        result.addProperty("imported", imported);
        result.addProperty("updated", updated);
        result.addProperty("skipped", skipped);
        return result;
    }

    private String uniqueDuplicateId(String baseId, java.util.Set<String> existingIds) {
        String candidate = baseId + "-copy";
        int index = 2;
        while (existingIds.contains(candidate)) {
            candidate = baseId + "-copy-" + index++;
        }
        return candidate;
    }

    private List<String> stringList(JsonObject object, String property) {
        List<String> values = new ArrayList<>();
        if (!object.has(property) || !object.get(property).isJsonArray()) {
            return values;
        }
        for (JsonElement element : object.getAsJsonArray(property)) {
            if (!element.isJsonNull()) {
                values.add(element.getAsString());
            }
        }
        return values;
    }

    private List<JsonObject> filterByIds(List<JsonObject> items, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            List<JsonObject> copies = new ArrayList<>();
            items.forEach(item -> copies.add(item.deepCopy()));
            return copies;
        }
        java.util.Set<String> selected = new java.util.HashSet<>(ids);
        List<JsonObject> filtered = new ArrayList<>();
        for (JsonObject item : items) {
            String id = string(item, "id");
            if (id != null && selected.contains(id)) {
                filtered.add(item.deepCopy());
            }
        }
        return filtered;
    }

    private Path writeExportFile(String namePrefix, JsonObject payload) throws IOException {
        Path root = context.getProject() != null && !context.getProject().isDisposed() && context.getProject().getBasePath() != null
            ? Paths.get(context.getProject().getBasePath())
            : Paths.get(System.getProperty("user.home"));
        Path exportDir = root.resolve("doc").resolve("exports");
        Files.createDirectories(exportDir);
        Path output = exportDir.resolve(namePrefix + "-" + EXPORT_TIMESTAMP.format(Instant.now()) + ".json");
        Files.writeString(output, gson.toJson(payload), StandardCharsets.UTF_8);
        return output;
    }

    private void copySkillIntoScope(Path source, Path targetDirectory, boolean codexScope) throws IOException {
        if (!Files.exists(source)) {
            throw new IOException("Skill path does not exist: " + source);
        }
        if (codexScope && !Files.isDirectory(source)) {
            throw new IOException("Codex skills must be imported from a directory: " + source.getFileName());
        }
        if (codexScope && !Files.exists(source.resolve("SKILL.md"))) {
            throw new IOException("Codex skill directory must contain SKILL.md: " + source.getFileName());
        }

        Path destination = targetDirectory.resolve(source.getFileName().toString());
        if (Files.isDirectory(source)) {
            copyDirectory(source, destination);
        } else {
            Files.createDirectories(destination.getParent());
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void copyDirectory(Path source, Path destination) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path current : stream.toList()) {
                Path relative = source.relativize(current);
                Path target = destination.resolve(relative.toString());
                if (Files.isDirectory(current)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(current, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private JsonObject projectSkills(String first, String second, String scope) {
        if (context.getProject() == null || context.getProject().isDisposed() || context.getProject().getBasePath() == null) {
            return new JsonObject();
        }
        return skillMap(Paths.get(context.getProject().getBasePath(), first, second), scope);
    }

    private static JsonObject skillMap(Path directory, String scope) {
        JsonObject skills = new JsonObject();
        if (!Files.isDirectory(directory)) {
            return skills;
        }
        try (Stream<Path> paths = Files.list(directory)) {
            paths.filter(path -> Files.isDirectory(path) || path.getFileName().toString().endsWith(".md"))
                .forEach(path -> {
                    String name = path.getFileName().toString();
                    String id = scope + "-" + name;
                    JsonObject skill = new JsonObject();
                    skill.addProperty("id", id);
                    skill.addProperty("name", name.replaceFirst("\\.md$", ""));
                    skill.addProperty("type", Files.isDirectory(path) ? "directory" : "file");
                    skill.addProperty("scope", scope);
                    skill.addProperty("path", path.toString());
                    skill.addProperty("enabled", !path.toString().contains("/.codemoss/skills-disabled/"));
                    skill.addProperty("skillPath", path.toString());
                    skills.add(id, skill);
                });
        } catch (Exception ignored) {
            // A partial skill list is preferable to failing the entire settings page.
        }
        return skills;
    }

    private Path skillBase(String scope, boolean enabled) {
        return switch (scope) {
            case "global" -> enabled
                ? Paths.get(System.getProperty("user.home"), ".claude", "skills")
                : Paths.get(System.getProperty("user.home"), ".codemoss", "skills-disabled", "global");
            case "local" -> enabled
                ? Paths.get(context.getProject().getBasePath(), ".claude", "skills")
                : Paths.get(System.getProperty("user.home"), ".codemoss", "skills-disabled", "local", projectKey());
            case "user" -> enabled
                ? Paths.get(System.getProperty("user.home"), ".agents", "skills")
                : Paths.get(System.getProperty("user.home"), ".codemoss", "skills-disabled", "user");
            case "repo" -> enabled
                ? Paths.get(context.getProject().getBasePath(), ".agents", "skills")
                : Paths.get(System.getProperty("user.home"), ".codemoss", "skills-disabled", "repo", projectKey());
            default -> throw new IllegalArgumentException("Unknown scope: " + scope);
        };
    }

    private String projectKey() {
        String basePath = context.getProject() != null ? context.getProject().getBasePath() : "default";
        return Integer.toHexString((basePath == null ? UUID.randomUUID().toString() : basePath).hashCode());
    }
}
