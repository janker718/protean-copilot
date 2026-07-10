package com.protean.copilot.handler;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;
import com.protean.copilot.handler.core.BaseMessageHandler;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.settings.CodemossSettingsService;
import com.protean.copilot.settings.manager.McpServerManager;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Handles WebView actions which are not part of a session, permission, or diff flow.
 * Keeping this protocol boundary explicit prevents settings pages from silently timing out.
 */
public final class FrontendActionCoverageHandler extends BaseMessageHandler {

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
                case "get_project_info" -> projectInfo();
                case "get_mcp_servers" -> mcpServers("window.updateMcpServers");
                case "get_codex_mcp_servers" -> mcpServers("window.updateCodexMcpServers");
                case "get_mcp_server_status" -> mcpStatus("window.updateMcpServerStatus");
                case "get_codex_mcp_server_status" -> mcpStatus("window.updateCodexMcpServerStatus");
                case "get_mcp_server_tools", "get_codex_mcp_server_tools" -> callback("window.updateMcpServerTools", "{\"tools\":[]}");
                case "get_all_skills" -> skills();
                case "get_linkify_capabilities" -> callback("window.updateLinkifyCapabilities", "{\"fileNavigationEnabled\":true,\"classNavigationEnabled\":false}");
                case "list_files" -> listFiles(content);
                case "undo_all_file_changes" -> callback("window.onUndoAllFileResult", "{\"success\":false,\"error\":\"Batch undo is not available for this session\"}");
                case "refresh_file", "rewind_files", "save_json", "open_file_chooser_for_cc_switch", "preview_cc_switch_import",
                     "save_imported_providers", "import_skill", "open_skill", "delete_skill", "toggle_skill", "export_agents",
                     "import_agents_file", "save_imported_agents", "export_prompts", "import_prompts_file", "save_imported_prompts",
                     "add_prompt", "update_prompt", "delete_prompt", "add_mcp_server", "update_mcp_server", "delete_mcp_server",
                     "toggle_mcp_server", "add_codex_mcp_server", "update_codex_mcp_server", "delete_codex_mcp_server", "toggle_codex_mcp_server" ->
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
        if (context.getProject() != null && !context.getProject().isDisposed()) {
            for (McpServerManager.McpServerDefinition definition : McpServerManager.getInstance(context.getProject()).listServers()) {
                JsonObject server = new JsonObject();
                server.addProperty("id", definition.name());
                server.addProperty("name", definition.name());
                server.addProperty("enabled", true);
                JsonObject spec = new JsonObject();
                spec.addProperty("type", "stdio");
                spec.addProperty("command", definition.command());
                spec.add("args", gson.toJsonTree(definition.args()));
                server.add("server", spec);
                servers.add(server);
            }
        }
        callback(callbackName, gson.toJson(servers));
    }

    private void mcpStatus(String callbackName) {
        JsonArray statuses = new JsonArray();
        if (context.getProject() != null && !context.getProject().isDisposed()) {
            for (McpServerManager.McpServerDefinition definition : McpServerManager.getInstance(context.getProject()).listServers()) {
                JsonObject status = new JsonObject();
                status.addProperty("name", definition.name());
                status.addProperty("status", "pending");
                statuses.add(status);
            }
        }
        callback(callbackName, gson.toJson(statuses));
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
                    skill.addProperty("enabled", true);
                    skills.add(id, skill);
                });
        } catch (Exception ignored) {
            // A partial skill list is preferable to failing the entire settings page.
        }
        return skills;
    }
}
