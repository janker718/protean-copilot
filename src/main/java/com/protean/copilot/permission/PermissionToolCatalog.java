package com.protean.copilot.permission;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Central registry for permission-controlled tools and their memory strategy.
 */
final class PermissionToolCatalog {

    private static final Set<String> FILE_MUTATION_TOOLS = Set.of(
        "Write",
        "Edit",
        "MultiEdit",
        "Delete",
        "CreateDirectory",
        "MoveFile",
        "CopyFile",
        "Rename"
    );

    private static final Set<String> COMMAND_EXECUTION_TOOLS = Set.of(
        "Bash",
        "ExecuteCommand"
    );

    private static final Set<String> DIFF_REVIEW_TOOLS = Set.of(
        "Write",
        "Edit"
    );

    private PermissionToolCatalog() {
    }

    static boolean isControlledTool(@Nullable String toolName) {
        return isFileMutationTool(toolName) || isCommandExecutionTool(toolName);
    }

    static boolean isFileMutationTool(@Nullable String toolName) {
        return toolName != null && FILE_MUTATION_TOOLS.contains(toolName);
    }

    static boolean isCommandExecutionTool(@Nullable String toolName) {
        return toolName != null && COMMAND_EXECUTION_TOOLS.contains(toolName);
    }

    static boolean supportsDiffReview(@Nullable String toolName) {
        return toolName != null && DIFF_REVIEW_TOOLS.contains(toolName);
    }

    static boolean isAcceptEditsEligible(@Nullable String toolName) {
        return toolName != null && Set.of(
            "Write",
            "Edit",
            "MultiEdit",
            "CreateDirectory",
            "MoveFile",
            "CopyFile",
            "Rename"
        ).contains(toolName);
    }

    static JsonObject normalizeInputs(@Nullable String toolName, @Nullable JsonObject inputs) {
        JsonObject normalized = new JsonObject();
        if (toolName == null) {
            return normalized;
        }
        if (inputs == null) {
            normalized.add("scope", JsonNull.INSTANCE);
            return normalized;
        }

        if (isCommandExecutionTool(toolName)) {
            copyIfPresent(inputs, normalized, "command");
            copyIfPresent(inputs, normalized, "cwd");
            copyIfPresent(inputs, normalized, "working_directory");
            return normalized;
        }

        if (isFileMutationTool(toolName)) {
            addPathScope(inputs, normalized);
            return normalized;
        }

        inputs.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> normalized.add(entry.getKey(), entry.getValue()));
        return normalized;
    }

    static JsonObject normalizeInputs(@Nullable String toolName, @Nullable Map<String, Object> inputs) {
        JsonObject asJson = new JsonObject();
        if (inputs != null) {
            inputs.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> addProperty(asJson, entry.getKey(), entry.getValue()));
        }
        return normalizeInputs(toolName, asJson);
    }

    private static void addPathScope(@NotNull JsonObject inputs, @NotNull JsonObject normalized) {
        Set<String> orderedPaths = new LinkedHashSet<>();
        collectPath(inputs, orderedPaths, "file_path");
        collectPath(inputs, orderedPaths, "path");
        collectPath(inputs, orderedPaths, "notebook_path");
        collectPath(inputs, orderedPaths, "source_path");
        collectPath(inputs, orderedPaths, "src");
        collectPath(inputs, orderedPaths, "from");
        collectPath(inputs, orderedPaths, "old_path");
        collectPath(inputs, orderedPaths, "destination_path");
        collectPath(inputs, orderedPaths, "dest");
        collectPath(inputs, orderedPaths, "to");
        collectPath(inputs, orderedPaths, "new_path");
        collectPath(inputs, orderedPaths, "target_path");

        if (orderedPaths.isEmpty()) {
            copyIfPresent(inputs, normalized, "cwd");
            copyIfPresent(inputs, normalized, "working_directory");
            return;
        }

        int index = 0;
        for (String path : orderedPaths) {
            normalized.addProperty("path" + index, path);
            index++;
        }
    }

    private static void collectPath(JsonObject inputs, Set<String> orderedPaths, String key) {
        if (!inputs.has(key) || inputs.get(key).isJsonNull()) {
            return;
        }
        orderedPaths.add(inputs.get(key).getAsString());
    }

    private static void copyIfPresent(JsonObject source, JsonObject target, String key) {
        if (source.has(key)) {
            target.add(key, source.get(key));
        }
    }

    private static void addProperty(JsonObject json, String key, Object value) {
        if (value == null) {
            json.add(key, JsonNull.INSTANCE);
            return;
        }
        if (value instanceof Boolean boolValue) {
            json.addProperty(key, boolValue);
            return;
        }
        if (value instanceof Number numberValue) {
            json.addProperty(key, numberValue);
            return;
        }
        json.addProperty(key, String.valueOf(value));
    }
}
