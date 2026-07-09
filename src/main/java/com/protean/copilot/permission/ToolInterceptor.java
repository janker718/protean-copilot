package com.protean.copilot.permission;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tool call interceptor.
 * Intercepts and handles tool call permissions before messages are sent to the SDK.
 */
public class ToolInterceptor {

    private static final Logger LOG = Logger.getInstance(ToolInterceptor.class);
    private final Project project;
    private final Set<String> controlledTools;

    public ToolInterceptor(Project project) {
        this.project = project;
        this.controlledTools = new HashSet<>(Arrays.asList(
            "Write",
            "Edit",
            "Delete",
            "Bash",
            "ExecuteCommand",
            "CreateDirectory",
            "MoveFile",
            "CopyFile"
        ));
    }

    public boolean needsPermission(String message) {
        String lowerMessage = message.toLowerCase(Locale.ROOT);
        return lowerMessage.contains("创建")
            || lowerMessage.contains("写入")
            || lowerMessage.contains("文件")
            || lowerMessage.contains("执行")
            || lowerMessage.contains("运行")
            || lowerMessage.contains("删除")
            || lowerMessage.contains("编辑");
    }

    public String preprocessMessage(String message) {
        if (!needsPermission(message)) {
            return "default";
        }

        AtomicBoolean userApproved = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        ApplicationManager.getApplication().invokeLater(() -> {
            int result = JOptionPane.showConfirmDialog(
                null,
                "Claude 需要执行以下操作：\n\n" + message + "\n\n是否允许执行？",
                "权限请求",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            );
            userApproved.set(result == JOptionPane.YES_OPTION);
            latch.countDown();
        });

        try {
            boolean responded = latch.await(30, TimeUnit.SECONDS);
            if (!responded) {
                LOG.warn("权限请求超时，自动拒绝");
                return null;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }

        return userApproved.get() ? "bypassPermissions" : null;
    }

    public CompletableFuture<Boolean> showDetailedPermissionDialog(String toolName, Map<String, Object> inputs) {
        JsonObject payload = new JsonObject();
        if (inputs != null) {
            inputs.forEach((key, value) -> {
                if (value == null) {
                    payload.add(key, JsonNull.INSTANCE);
                } else {
                    payload.addProperty(key, String.valueOf(value));
                }
            });
        }
        PermissionService service = PermissionService.findRegisteredInstance(project);
        if (service == null) {
            return showLegacyDetailedPermissionDialog(toolName, inputs);
        }
        return service.requestLocalPermission(toolName, payload, null, project)
            .thenApply(result -> result != null
                && result.getBehavior() == PermissionRequest.PermissionResult.Behavior.ALLOW);
    }

    private CompletableFuture<Boolean> showLegacyDetailedPermissionDialog(String toolName, Map<String, Object> inputs) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        ApplicationManager.getApplication().invokeLater(() -> {
            StringBuilder message = new StringBuilder();
            message.append("Tool: ").append(toolName).append("\n");
            if (inputs != null) {
                inputs.forEach((key, value) -> message.append(key).append(": ").append(value).append("\n"));
            }
            int result = JOptionPane.showConfirmDialog(
                null,
                message.toString(),
                "Permission Request",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            );
            future.complete(result == JOptionPane.YES_OPTION);
        });
        return future;
    }

    public List<ToolCall> parseToolCalls(String sdkResponse) {
        List<ToolCall> toolCalls = new ArrayList<>();
        try {
            JsonObject response = JsonParser.parseString(sdkResponse).getAsJsonObject();
            if (response.has("message")) {
                JsonObject message = response.getAsJsonObject("message");
                if (message.has("content")) {
                    JsonArray content = message.getAsJsonArray("content");
                    for (JsonElement element : content) {
                        if (element.isJsonObject()) {
                            JsonObject contentItem = element.getAsJsonObject();
                            if (contentItem.has("type") && "tool_use".equals(contentItem.get("type").getAsString())) {
                                String toolName = contentItem.get("name").getAsString();
                                if (!controlledTools.contains(toolName)) {
                                    continue;
                                }
                                JsonObject inputs = contentItem.getAsJsonObject("input");
                                ToolCall call = new ToolCall();
                                call.toolName = toolName;
                                call.inputs = new HashMap<>();
                                for (Map.Entry<String, JsonElement> entry : inputs.entrySet()) {
                                    call.inputs.put(entry.getKey(), entry.getValue().toString());
                                }
                                toolCalls.add(call);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return toolCalls;
    }

    public static class ToolCall {
        public String toolName;
        public Map<String, Object> inputs;
    }
}
