package com.protean.copilot.permission;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.UUID;

/**
 * 权限对话框和访问控制的占位实现。
 * 当 SDK 后端连接后，将管理权限请求。
 */
public class PermissionService {

    private static final Logger LOG = Logger.getInstance(PermissionService.class);
    private static final int DIALOG_SHOWER_POLL_INTERVAL_MS = 100;
    private static final int DIALOG_SHOWER_POLL_MAX_ATTEMPTS = 20;

    public enum PermissionResponse {
        ALLOW(1, "Allow"),
        ALLOW_ALWAYS(2, "Allow and don't ask again"),
        DENY(3, "Deny");

        private final int value;
        private final String description;

        PermissionResponse(int value, String description) {
            this.value = value;
            this.description = description;
        }

        public int getValue() {
            return value;
        }

        public String getDescription() {
            return description;
        }

        public static PermissionResponse fromValue(int value) {
            for (PermissionResponse response : values()) {
                if (response.value == value) {
                    return response;
                }
            }
            return null;
        }

        public boolean isAllow() {
            return this == ALLOW || this == ALLOW_ALWAYS;
        }
    }

    private final Project project;
    private final Path permissionDir;
    private final String sessionId;
    private final Gson gson = new Gson();
    private volatile long lastActivityTime = System.currentTimeMillis();
    private volatile PermissionDecisionListener decisionListener;

    private final PermissionDecisionStore decisionStore;
    private final PermissionManager permissionManager;
    private final PermissionDialogRouter dialogRouter;
    private final PermissionFileProtocol fileProtocol;
    private final PermissionRequestWatcher requestWatcher;
    private final Set<String> processingRequests = ConcurrentHashMap.newKeySet();

    PermissionService(Project project, String sessionId) {
        this.project = project;
        this.sessionId = sessionId;
        String envDir = System.getenv("CLAUDE_PERMISSION_DIR");
        this.permissionDir = envDir != null && !envDir.trim().isEmpty()
            ? Paths.get(envDir)
            : Paths.get(System.getProperty("java.io.tmpdir"), "claude-permission");
        try {
            Files.createDirectories(permissionDir);
        } catch (Exception e) {
            LOG.error("Failed to create permission dir", e);
        }
        this.decisionStore = new PermissionDecisionStore();
        this.permissionManager = new PermissionManager(decisionStore);
        this.dialogRouter = new PermissionDialogRouter((tag, message) -> LOG.debug("[" + tag + "] " + message));
        this.fileProtocol = new PermissionFileProtocol(permissionDir, sessionId, gson, (tag, message) -> LOG.debug("[" + tag + "] " + message));
        this.requestWatcher = new PermissionRequestWatcher(permissionDir, sessionId, fileProtocol, (tag, message) -> LOG.debug("[" + tag + "] " + message));
    }

    /**
     * 为项目和会话获取或创建一个 PermissionService 实例。
     */
    public static synchronized PermissionService getInstance(Project project, String sessionId) {
        return PermissionSessionRegistry.getInstance(
            sessionId,
            () -> new PermissionService(project, PermissionSessionRegistry.newLegacySessionId()),
            sid -> new PermissionService(project, sid)
        );
    }

    @Deprecated(since = "0.1.6", forRemoval = true)
    public static synchronized PermissionService getInstance(Project project) {
        return PermissionSessionRegistry.getLegacyInstance(
            () -> new PermissionService(project, PermissionSessionRegistry.newLegacySessionId())
        );
    }

    /**
     * 移除指定会话的 PermissionService 实例。
     */
    public static synchronized void removeInstance(String sessionId) {
        PermissionSessionRegistry.removeInstance(sessionId);
    }

    public static synchronized PermissionService findRegisteredInstance(Project project) {
        return PermissionSessionRegistry.findAnyInstanceForProject(project);
    }

    public interface PermissionDecisionListener {
        void onDecision(PermissionDecision decision);
    }

    public interface PermissionDialogShower {
        CompletableFuture<Integer> showPermissionDialog(String toolName, JsonObject inputs);
    }

    public interface AskUserQuestionDialogShower {
        CompletableFuture<JsonObject> showAskUserQuestionDialog(String requestId, JsonObject questions);
    }

    public interface PlanApprovalDialogShower {
        CompletableFuture<JsonObject> showPlanApprovalDialog(String requestId, JsonObject planData);
    }

    public static class PermissionDecision {
        private final String toolName;
        private final JsonObject inputs;
        private final PermissionResponse response;

        public PermissionDecision(String toolName, JsonObject inputs, PermissionResponse response) {
            this.toolName = toolName;
            this.inputs = inputs;
            this.response = response;
        }

        public String getToolName() {
            return toolName;
        }

        public JsonObject getInputs() {
            return inputs;
        }

        public PermissionResponse getResponse() {
            return response;
        }

        public boolean isAllowed() {
            return response != null && response.isAllow();
        }
    }

    /**
     * 为项目取消注册对话框显示器。
     */
    public void registerDialogShower(Project project, PermissionDialogShower shower) {
        dialogRouter.registerPermissionDialogShower(project, shower);
    }

    public void unregisterDialogShower(Project project) {
        dialogRouter.unregisterPermissionDialogShower(project);
    }

    @Deprecated
    public void setDialogShower(PermissionDialogShower shower) {
        if (shower != null && this.project != null) {
            dialogRouter.registerPermissionDialogShower(this.project, shower);
        }
    }

    public void registerAskUserQuestionDialogShower(Project project, AskUserQuestionDialogShower shower) {
        dialogRouter.registerAskUserQuestionDialogShower(project, shower);
    }

    public void unregisterAskUserQuestionDialogShower(Project project) {
        dialogRouter.unregisterAskUserQuestionDialogShower(project);
    }

    public void registerPlanApprovalDialogShower(Project project, PlanApprovalDialogShower shower) {
        dialogRouter.registerPlanApprovalDialogShower(project, shower);
    }

    public void unregisterPlanApprovalDialogShower(Project project) {
        dialogRouter.unregisterPlanApprovalDialogShower(project);
    }

    public void setLastActiveProject(Project project) {
        dialogRouter.setLastActiveProject(project);
    }

    public void setDecisionListener(PermissionDecisionListener listener) {
        this.decisionListener = listener;
        touch();
    }

    public void setPermissionMode(@Nullable String mode) {
        permissionManager.setPermissionMode(mapPermissionMode(mode));
        touch();
    }

    public CompletableFuture<PermissionRequest.PermissionResult> requestLocalPermission(
        @NotNull String toolName,
        @Nullable JsonObject inputs,
        @Nullable JsonObject suggestions,
        @Nullable Project ownerProject
    ) {
        touch();
        PermissionRequest request = permissionManager.createRequest(
            UUID.randomUUID().toString(),
            toolName,
            toInputMap(inputs),
            suggestions,
            ownerProject != null ? ownerProject : project
        );
        return request.getResultFuture();
    }

    /**
     * 清除权限决策记忆。
     */
    public void clearDecisionMemory() {
        permissionManager.clearPermissionMemory();
        decisionStore.clear();
    }

    public Project getProject() {
        return project;
    }

    public String getSessionId() {
        return sessionId;
    }

    public PermissionManager getPermissionManager() {
        touch();
        return permissionManager;
    }

    public void setOnPermissionRequestedCallback(Consumer<PermissionRequest> callback) {
        permissionManager.setOnPermissionRequestedCallback(callback);
        touch();
    }

    public void start() {
        touch();
        requestWatcher.start(new PermissionRequestWatcher.RequestHandler() {
            @Override
            public void handlePermissionRequest(Path requestFile) {
                PermissionService.this.handlePermissionRequest(requestFile);
            }

            @Override
            public void handleAskUserQuestionRequest(Path requestFile) {
                PermissionService.this.handleAskUserQuestionRequest(requestFile);
            }

            @Override
            public void handlePlanApprovalRequest(Path requestFile) {
                PermissionService.this.handlePlanApprovalRequest(requestFile);
            }
        });
    }

    public void stop() {
        permissionManager.cancelAllPendingRequests();
        requestWatcher.stop();
    }

    long getLastActivityTime() {
        return lastActivityTime;
    }

    private void touch() {
        lastActivityTime = System.currentTimeMillis();
    }

    private PermissionManager.PermissionMode mapPermissionMode(@Nullable String mode) {
        if (mode == null) {
            return PermissionManager.PermissionMode.DEFAULT;
        }
        return switch (mode) {
            case "acceptEdits" -> PermissionManager.PermissionMode.ACCEPT_EDITS;
            case "bypassPermissions" -> PermissionManager.PermissionMode.ALLOW_ALL;
            case "default", "plan" -> PermissionManager.PermissionMode.DEFAULT;
            default -> PermissionManager.PermissionMode.DEFAULT;
        };
    }

    private Map<String, Object> toInputMap(@Nullable JsonObject inputs) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (inputs == null) {
            return result;
        }
        inputs.entrySet().forEach(entry -> {
            if (entry.getValue() == null || entry.getValue().isJsonNull()) {
                result.put(entry.getKey(), null);
            } else if (entry.getValue().isJsonPrimitive()) {
                if (entry.getValue().getAsJsonPrimitive().isBoolean()) {
                    result.put(entry.getKey(), entry.getValue().getAsBoolean());
                } else if (entry.getValue().getAsJsonPrimitive().isNumber()) {
                    result.put(entry.getKey(), entry.getValue().getAsNumber());
                } else {
                    result.put(entry.getKey(), entry.getValue().getAsString());
                }
            } else {
                result.put(entry.getKey(), entry.getValue().toString());
            }
        });
        return result;
    }

    private String acquireRequestContent(Path requestFile, String logTag) {
        String fileName = requestFile.getFileName().toString();
        if (!processingRequests.add(fileName)) {
            return null;
        }
        if (!fileProtocol.waitForFileReady(requestFile)) {
            processingRequests.remove(fileName);
            return null;
        }
        try {
            return Files.readString(requestFile);
        } catch (Exception e) {
            LOG.debug("[" + logTag + "] Failed to read request file: " + e.getMessage());
            processingRequests.remove(fileName);
            return null;
        }
    }

    private void safeDeleteFile(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (Exception ignored) {
        }
    }

    private PermissionResponse resolveDecision(int responseValue) {
        PermissionResponse decision = PermissionResponse.fromValue(responseValue);
        return decision != null ? decision : PermissionResponse.DENY;
    }

    private void notifyDecision(String toolName, JsonObject inputs, PermissionResponse response) {
        PermissionDecisionListener listener = decisionListener;
        if (listener == null || response == null) {
            return;
        }
        listener.onDecision(new PermissionDecision(toolName, inputs, response));
    }

    private void handlePermissionRequest(Path requestFile) {
        String fileName = requestFile.getFileName().toString();
        this.lastActivityTime = System.currentTimeMillis();

        String content = acquireRequestContent(requestFile, "PERM");
        if (content == null) {
            return;
        }

        try {
            JsonObject request = gson.fromJson(content, JsonObject.class);
            String requestId = request.get("requestId").getAsString();
            String toolName = request.get("toolName").getAsString();
            JsonObject inputs = request.get("inputs").getAsJsonObject();

            PermissionResponse rememberedDecision = decisionStore.getParameterDecision(toolName, inputs);
            if (rememberedDecision == null) {
                rememberedDecision = decisionStore.getToolDecision(toolName);
            }
            if (rememberedDecision != null) {
                boolean allow = rememberedDecision.isAllow();
                fileProtocol.writePermissionResponse(requestId, allow);
                notifyDecision(toolName, inputs, rememberedDecision);
                safeDeleteFile(requestFile);
                return;
            }

            if (DiffReviewService.isFileModifyingTool(toolName) && tryDiffReview(request, requestFile, fileName, requestId, toolName, inputs)) {
                return;
            }

            PermissionDialogShower shower = dialogRouter.findPermissionDialogShower(request, "MATCH_PROJECT");
            if (shower != null) {
                safeDeleteFile(requestFile);
                dispatchPermissionDialog(shower, requestId, toolName, inputs, fileName);
            } else {
                dispatchPermissionFallback(requestId, toolName, inputs, requestFile, fileName);
            }
        } catch (Exception e) {
            LOG.error("Error handling permission request", e);
        } finally {
            processingRequests.remove(fileName);
        }
    }

    private void dispatchPermissionDialog(PermissionDialogShower shower, String requestId, String toolName, JsonObject inputs, String fileName) {
        processingRequests.add(fileName);
        CompletableFuture<Integer> future = shower.showPermissionDialog(toolName, inputs);
        future.thenAccept(response -> {
            try {
                PermissionResponse decision = resolveDecision(response);
                boolean allow = decision.isAllow();
                if (decision == PermissionResponse.ALLOW_ALWAYS) {
                    decisionStore.rememberParameterDecision(toolName, inputs, PermissionResponse.ALLOW_ALWAYS);
                }
                notifyDecision(toolName, inputs, decision);
                fileProtocol.writePermissionResponse(requestId, allow);
            } finally {
                processingRequests.remove(fileName);
            }
        }).exceptionally(ex -> {
            fileProtocol.writePermissionResponse(requestId, false);
            notifyDecision(toolName, inputs, PermissionResponse.DENY);
            processingRequests.remove(fileName);
            return null;
        });
    }

    private void dispatchPermissionFallback(String requestId, String toolName, JsonObject inputs, Path requestFile, String fileName) {
        try {
            CompletableFuture<Integer> future = new CompletableFuture<>();
            ApplicationManager.getApplication().invokeLater(() ->
                future.complete(showSystemPermissionDialog(toolName, inputs)));
            int response = future.get(30, TimeUnit.SECONDS);
            PermissionResponse decision = resolveDecision(response);
            boolean allow = decision.isAllow();
            if (decision == PermissionResponse.ALLOW_ALWAYS) {
                decisionStore.rememberParameterDecision(toolName, inputs, PermissionResponse.ALLOW_ALWAYS);
            }
            notifyDecision(toolName, inputs, decision);
            fileProtocol.writePermissionResponse(requestId, allow);
            safeDeleteFile(requestFile);
        } catch (Exception e) {
            LOG.error("Fallback permission dialog failed", e);
            processingRequests.remove(fileName);
        }
    }

    private int showSystemPermissionDialog(String toolName, JsonObject inputs) {
        StringBuilder message = new StringBuilder();
        message.append("Tool: ").append(toolName).append("\n");
        if (inputs.has("file_path")) {
            message.append("File: ").append(inputs.get("file_path").getAsString()).append("\n");
        }
        if (inputs.has("command")) {
            message.append("Command: ").append(inputs.get("command").getAsString()).append("\n");
        }
        Object[] options = {"Allow", "Deny"};
        int result = JOptionPane.showOptionDialog(
            null,
            message.toString(),
            "Permission Request - " + toolName,
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]
        );
        return result == 0 ? PermissionResponse.ALLOW.getValue() : PermissionResponse.DENY.getValue();
    }

    private void handleAskUserQuestionRequest(Path requestFile) {
        String fileName = requestFile.getFileName().toString();
        String content = acquireRequestContent(requestFile, "ASK");
        if (content == null) {
            return;
        }
        safeDeleteFile(requestFile);
        try {
            JsonObject request = gson.fromJson(content, JsonObject.class);
            String requestId = request.get("requestId").getAsString();
            AskUserQuestionDialogShower shower = dialogRouter.findAskUserQuestionDialogShower(request);
            if (shower != null) {
                CompletableFuture<JsonObject> future = shower.showAskUserQuestionDialog(requestId, request);
                future.thenAccept(answers -> {
                    fileProtocol.writeAskUserQuestionResponse(requestId, answers);
                    processingRequests.remove(fileName);
                }).exceptionally(ex -> {
                    fileProtocol.writeAskUserQuestionResponse(requestId, new JsonObject());
                    processingRequests.remove(fileName);
                    return null;
                });
            } else {
                fileProtocol.writeAskUserQuestionResponse(requestId, new JsonObject());
                processingRequests.remove(fileName);
            }
        } catch (Exception e) {
            processingRequests.remove(fileName);
        }
    }

    private void handlePlanApprovalRequest(Path requestFile) {
        String fileName = requestFile.getFileName().toString();
        String content = acquireRequestContent(requestFile, "PLAN");
        if (content == null) {
            return;
        }
        try {
            JsonObject request = gson.fromJson(content, JsonObject.class);
            String requestId = request.get("requestId").getAsString();
            safeDeleteFile(requestFile);
            PlanApprovalDialogShower shower = dialogRouter.findPlanApprovalDialogShower(request);
            if (shower != null) {
                CompletableFuture<JsonObject> future = shower.showPlanApprovalDialog(requestId, request);
                future.thenAccept(response -> {
                    boolean approved = response.has("approved") && response.get("approved").getAsBoolean();
                    String targetMode = response.has("targetMode") ? response.get("targetMode").getAsString() : "default";
                    fileProtocol.writePlanApprovalResponse(requestId, approved, targetMode);
                    processingRequests.remove(fileName);
                }).exceptionally(ex -> {
                    fileProtocol.writePlanApprovalResponse(requestId, false, "default");
                    processingRequests.remove(fileName);
                    return null;
                });
            } else {
                fileProtocol.writePlanApprovalResponse(requestId, false, "default");
                processingRequests.remove(fileName);
            }
        } catch (Exception e) {
            processingRequests.remove(fileName);
        }
    }

    private boolean tryDiffReview(JsonObject request, Path requestFile, String fileName, String requestId, String toolName, JsonObject inputs) {
        Project matched = waitForDialogRegistration(request);
        if (matched == null) {
            return false;
        }

        CompletableFuture<DiffReviewResult> reviewFuture = DiffReviewService.reviewFileChange(matched, toolName, inputs);
        if (reviewFuture == null) {
            return false;
        }

        safeDeleteFile(requestFile);
        reviewFuture.thenAccept(result -> {
            handleDiffReviewResult(result, requestId, toolName, inputs);
            processingRequests.remove(fileName);
        }).exceptionally(ex -> {
            fileProtocol.writePermissionResponse(requestId, false);
            notifyDecision(toolName, inputs, PermissionResponse.DENY);
            processingRequests.remove(fileName);
            return null;
        });

        return true;
    }

    private Project waitForDialogRegistration(JsonObject request) {
        Project matched = dialogRouter.findProjectByCwd(request);
        if (matched != null || dialogRouter.getPermissionDialogCount() > 0) {
            return matched;
        }
        try {
            for (int i = 0; i < DIALOG_SHOWER_POLL_MAX_ATTEMPTS && dialogRouter.getPermissionDialogCount() == 0; i++) {
                Thread.sleep(DIALOG_SHOWER_POLL_INTERVAL_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return dialogRouter.findProjectByCwd(request);
    }

    private void handleDiffReviewResult(DiffReviewResult result, String requestId, String toolName, JsonObject inputs) {
        try {
            if (result.isAccepted()) {
                if (result.isAlwaysAllow()) {
                    decisionStore.rememberToolDecision(toolName, PermissionResponse.ALLOW_ALWAYS);
                }
                fileProtocol.writePermissionResponse(requestId, true);
                notifyDecision(toolName, inputs, result.isAlwaysAllow() ? PermissionResponse.ALLOW_ALWAYS : PermissionResponse.ALLOW);
            } else {
                fileProtocol.writePermissionResponse(requestId, false);
                notifyDecision(toolName, inputs, PermissionResponse.DENY);
            }
        } catch (Exception e) {
            fileProtocol.writePermissionResponse(requestId, false);
            notifyDecision(toolName, inputs, PermissionResponse.DENY);
        }
    }
}
