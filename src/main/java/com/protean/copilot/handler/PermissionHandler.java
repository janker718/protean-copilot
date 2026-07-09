package com.protean.copilot.handler;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.protean.copilot.handler.core.BaseMessageHandler;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.permission.PermissionRequest;
import com.protean.copilot.permission.PermissionService;
import com.protean.copilot.settings.CodemossSettingsService;
import com.protean.copilot.ui.toolwindow.ProteanChatWindow;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import com.intellij.util.concurrency.AppExecutorUtil;

/**
 * Permission handler.
 * Handles permission dialog display and decision processing.
 */
public class PermissionHandler extends BaseMessageHandler {

    private static final Logger LOG = Logger.getInstance(PermissionHandler.class);

    private static final List<String> SUPPORTED_TYPES = List.of(
        "permission_decision",
        "ask_user_question_response",
        "plan_approval_response"
    );

    private final Map<String, CompletableFuture<Integer>> pendingPermissionRequests = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<JsonObject>> pendingAskUserQuestionRequests = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<JsonObject>> pendingPlanApprovalRequests = new ConcurrentHashMap<>();
    private volatile PermissionService permissionService;
    private final SafetyNetScheduler safetyNetScheduler;

    interface CancellableTask {
        void cancel();
    }

    interface SafetyNetScheduler {
        CancellableTask schedule(Runnable task, long delaySeconds);
    }

    private static final SafetyNetScheduler DEFAULT_SAFETY_NET_SCHEDULER = (task, delaySeconds) -> {
        ScheduledFuture<?> scheduledFuture = AppExecutorUtil.getAppScheduledExecutorService()
            .schedule(task, delaySeconds, TimeUnit.SECONDS);
        return () -> scheduledFuture.cancel(false);
    };

    public interface PermissionDeniedCallback {
        void onPermissionDenied();
    }

    private PermissionDeniedCallback deniedCallback;

    public PermissionHandler(@NotNull HandlerContext context) {
        this(context, DEFAULT_SAFETY_NET_SCHEDULER);
    }

    PermissionHandler(@NotNull HandlerContext context, SafetyNetScheduler safetyNetScheduler) {
        super(context);
        this.safetyNetScheduler = safetyNetScheduler;
    }

    public void setPermissionDeniedCallback(PermissionDeniedCallback callback) {
        this.deniedCallback = callback;
    }

    public void bindPermissionService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    long getSafetyNetTimeoutSeconds() {
        try {
            return new CodemossSettingsService().getPermissionDialogTimeoutSeconds()
                + CodemossSettingsService.PERMISSION_SAFETY_NET_BUFFER_SECONDS;
        } catch (Exception e) {
            LOG.warn("读取权限弹窗超时设置失败: " + e.getMessage(), e);
            return CodemossSettingsService.DEFAULT_PERMISSION_DIALOG_TIMEOUT_SECONDS
                + CodemossSettingsService.PERMISSION_SAFETY_NET_BUFFER_SECONDS;
        }
    }

    void scheduleSafetyNet(CompletableFuture<?> future, Runnable timeoutTask) {
        CancellableTask cancellableTask = safetyNetScheduler.schedule(timeoutTask, getSafetyNetTimeoutSeconds());
        future.whenComplete((ignored, error) -> cancellableTask.cancel());
    }

    @Override
    public boolean handle(String type, String content) {
        if ("permission_decision".equals(type)) {
            handlePermissionDecision(content);
            return true;
        } else if ("ask_user_question_response".equals(type)) {
            handleAskUserQuestionResponse(content);
            return true;
        } else if ("plan_approval_response".equals(type)) {
            handlePlanApprovalResponse(content);
            return true;
        }
        return false;
    }

    @Override
    public List<String> getSupportedTypes() {
        return SUPPORTED_TYPES;
    }

    public CompletableFuture<Integer> showFrontendPermissionDialog(String toolName, JsonObject inputs) {
        String channelId = UUID.randomUUID().toString();
        CompletableFuture<Integer> future = new CompletableFuture<>();
        pendingPermissionRequests.put(channelId, future);
        try {
            Gson gson = new Gson();
            JsonObject requestData = new JsonObject();
            requestData.addProperty("channelId", channelId);
            requestData.addProperty("toolName", toolName);
            requestData.add("inputs", inputs);
            String escapedJson = escapeJs(gson.toJson(requestData));
            ApplicationManager.getApplication().invokeLater(() ->
                context.executeJavaScriptOnEDT(
                    "(function retryShowDialog(retries) { " +
                        "if (window.showPermissionDialog) { " +
                        "window.showPermissionDialog('" + escapedJson + "'); " +
                        "} else if (retries > 0) { " +
                        "setTimeout(function() { retryShowDialog(retries - 1); }, 200); " +
                        "} else { " +
                        "console.error('showPermissionDialog not available'); " +
                        "} " +
                    "})(30);"
                )
            );
            scheduleSafetyNet(future, () -> {
                if (future.complete(PermissionService.PermissionResponse.DENY.getValue())) {
                    pendingPermissionRequests.remove(channelId);
                }
            });
        } catch (Exception e) {
            pendingPermissionRequests.remove(channelId);
            future.complete(PermissionService.PermissionResponse.DENY.getValue());
        }
        return future;
    }

    public void showPermissionDialog(PermissionRequest request) {
        try {
            Gson gson = new Gson();
            JsonObject requestData = new JsonObject();
            requestData.addProperty("channelId", request.getChannelId());
            requestData.addProperty("toolName", request.getToolName());
            JsonObject inputsJson = gson.toJsonTree(request.getInputs()).getAsJsonObject();
            requestData.add("inputs", inputsJson);
            if (request.getSuggestions() != null) {
                requestData.add("suggestions", request.getSuggestions());
            }
            String escapedJson = escapeJs(gson.toJson(requestData));

            Project targetProject = request.getProject() != null ? request.getProject() : context.getProject();
            ProteanChatWindow targetWindow = ProteanChatWindow.getChatWindow(targetProject);
            if (targetWindow == null) {
                request.reject("Failed to show permission dialog: window not found", true);
                notifyPermissionDenied();
                return;
            }

            targetWindow.executeJavaScriptCode(
                "if (window.showPermissionDialog) { window.showPermissionDialog('" + escapedJson + "'); }"
            );
        } catch (Exception e) {
            LOG.warn("显示权限弹窗失败: " + e.getMessage(), e);
            request.reject("Failed to show permission dialog", true);
            notifyPermissionDenied();
        }
    }

    public CompletableFuture<JsonObject> showAskUserQuestionDialog(String requestId, JsonObject requestData) {
        String resolvedRequestId = requestId != null ? requestId
            : (requestData.has("requestId") ? requestData.get("requestId").getAsString() : UUID.randomUUID().toString());
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        pendingAskUserQuestionRequests.put(resolvedRequestId, future);
        context.executeJavaScriptOnEDT(
            "(function retryShowAskUserQuestion(retries) { " +
                "if (window.showAskUserQuestionDialog) { " +
                "window.showAskUserQuestionDialog('" + escapeJs(requestData.toString()) + "'); " +
                "} else if (retries > 0) { " +
                "setTimeout(function() { retryShowAskUserQuestion(retries - 1); }, 200); " +
                "} " +
            "})(30);"
        );
        scheduleSafetyNet(future, () -> {
            if (future.complete(new JsonObject())) {
                pendingAskUserQuestionRequests.remove(resolvedRequestId);
            }
        });
        return future;
    }

    public CompletableFuture<JsonObject> showPlanApprovalDialog(String requestId, JsonObject requestData) {
        String resolvedRequestId = requestId != null ? requestId
            : (requestData.has("requestId") ? requestData.get("requestId").getAsString() : UUID.randomUUID().toString());
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        pendingPlanApprovalRequests.put(resolvedRequestId, future);
        context.executeJavaScriptOnEDT(
            "(function retryShowPlanApproval(retries) { " +
                "if (window.showPlanApprovalDialog) { " +
                "window.showPlanApprovalDialog('" + escapeJs(requestData.toString()) + "'); " +
                "} else if (retries > 0) { " +
                "setTimeout(function() { retryShowPlanApproval(retries - 1); }, 200); " +
                "} " +
            "})(30);"
        );
        scheduleSafetyNet(future, () -> {
            JsonObject timeoutResponse = new JsonObject();
            timeoutResponse.addProperty("approved", false);
            timeoutResponse.addProperty("targetMode", "default");
            timeoutResponse.addProperty("message", "Plan approval timed out");
            if (future.complete(timeoutResponse)) {
                pendingPlanApprovalRequests.remove(resolvedRequestId);
            }
        });
        return future;
    }

    public void clearPendingRequests() {
        for (Map.Entry<String, CompletableFuture<Integer>> entry : pendingPermissionRequests.entrySet()) {
            entry.getValue().complete(PermissionService.PermissionResponse.DENY.getValue());
        }
        pendingPermissionRequests.clear();

        for (Map.Entry<String, CompletableFuture<JsonObject>> entry : pendingAskUserQuestionRequests.entrySet()) {
            entry.getValue().completeExceptionally(new IllegalStateException("Ask user question cancelled"));
        }
        pendingAskUserQuestionRequests.clear();

        for (Map.Entry<String, CompletableFuture<JsonObject>> entry : pendingPlanApprovalRequests.entrySet()) {
            entry.getValue().completeExceptionally(new IllegalStateException("Plan approval cancelled"));
        }
        pendingPlanApprovalRequests.clear();
    }

    private void handlePermissionDecision(String jsonContent) {
        try {
            Gson gson = new Gson();
            JsonObject decision = gson.fromJson(jsonContent, JsonObject.class);
            String channelId = decision.get("channelId").getAsString();
            boolean allow = decision.get("allow").getAsBoolean();
            boolean remember = decision.get("remember").getAsBoolean();
            String rejectMessage = decision.has("rejectMessage") && !decision.get("rejectMessage").isJsonNull()
                ? decision.get("rejectMessage").getAsString() : "";

            CompletableFuture<Integer> pendingFuture = pendingPermissionRequests.remove(channelId);
            if (pendingFuture != null) {
                int responseValue = allow
                    ? (remember ? PermissionService.PermissionResponse.ALLOW_ALWAYS.getValue() : PermissionService.PermissionResponse.ALLOW.getValue())
                    : PermissionService.PermissionResponse.DENY.getValue();
                pendingFuture.complete(responseValue);
            }

            PermissionService service = permissionService;
            if (remember && allow) {
                if (service != null) {
                    service.getPermissionManager().handlePermissionDecisionAlways(channelId, true);
                }
            } else if (service != null) {
                service.getPermissionManager().handlePermissionDecision(channelId, allow, remember, rejectMessage);
            }

            if (!allow) {
                notifyPermissionDenied();
            }
        } catch (Exception e) {
            LOG.warn("处理权限决策失败: " + e.getMessage(), e);
        }
    }

    private void handleAskUserQuestionResponse(String jsonContent) {
        try {
            Gson gson = new Gson();
            JsonObject response = gson.fromJson(jsonContent, JsonObject.class);
            String requestId = response.get("requestId").getAsString();
            JsonObject answers = response.has("answers") && response.get("answers").isJsonObject()
                ? response.getAsJsonObject("answers")
                : new JsonObject();
            CompletableFuture<JsonObject> pendingFuture = pendingAskUserQuestionRequests.remove(requestId);
            if (pendingFuture != null) {
                pendingFuture.complete(answers);
            }
        } catch (Exception e) {
            LOG.warn("处理 ask user question 响应失败: " + e.getMessage(), e);
        }
    }

    private void handlePlanApprovalResponse(String jsonContent) {
        try {
            Gson gson = new Gson();
            JsonObject response = gson.fromJson(jsonContent, JsonObject.class);
            String requestId = response.get("requestId").getAsString();
            CompletableFuture<JsonObject> pendingFuture = pendingPlanApprovalRequests.remove(requestId);
            if (pendingFuture != null) {
                pendingFuture.complete(response);
            }
        } catch (Exception e) {
            LOG.warn("处理 plan approval 响应失败: " + e.getMessage(), e);
        }
    }

    private void notifyPermissionDenied() {
        PermissionDeniedCallback callback = deniedCallback;
        if (callback != null) {
            callback.onPermissionDenied();
        }
    }
}
