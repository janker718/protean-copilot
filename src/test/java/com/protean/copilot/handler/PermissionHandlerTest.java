package com.protean.copilot.handler;

import com.google.gson.JsonObject;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.permission.PermissionService;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PermissionHandlerTest {

    @Test
    public void clearPendingRequestsCompletesPermissionFuturesWithDeny() throws Exception {
        PermissionHandler handler = new PermissionHandler(createContext());
        CompletableFuture<Integer> future = new CompletableFuture<>();
        getPermissionMap(handler).put("perm-1", future);

        handler.clearPendingRequests();

        assertEquals(PermissionService.PermissionResponse.DENY.getValue(), future.join().intValue());
        assertTrue(getPermissionMap(handler).isEmpty());
    }

    @Test
    public void clearPendingRequestsCompletesAskUserFuturesWithEmptyObject() throws Exception {
        PermissionHandler handler = new PermissionHandler(createContext());
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        getAskUserMap(handler).put("ask-1", future);

        handler.clearPendingRequests();

        JsonObject result = future.join();
        assertTrue(result.entrySet().isEmpty());
        assertTrue(getAskUserMap(handler).isEmpty());
    }

    @Test
    public void clearPendingRequestsCompletesPlanApprovalFuturesWithSessionChangedReject() throws Exception {
        PermissionHandler handler = new PermissionHandler(createContext());
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        getPlanApprovalMap(handler).put("plan-1", future);

        handler.clearPendingRequests();

        JsonObject result = future.join();
        assertFalse(result.get("approved").getAsBoolean());
        assertEquals("default", result.get("targetMode").getAsString());
        assertEquals("Session changed", result.get("message").getAsString());
        assertTrue(getPlanApprovalMap(handler).isEmpty());
    }

    @Test
    public void askUserSafetyNet_timesOutWithEmptyObject() throws Exception {
        ManualSafetyNetScheduler scheduler = new ManualSafetyNetScheduler();
        PermissionHandler handler = new PermissionHandler(createContext(), scheduler);
        Map<String, CompletableFuture<JsonObject>> askUserMap = getAskUserMap(handler);
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        askUserMap.put("ask-timeout", future);

        handler.scheduleSafetyNet(future, () -> {
            if (future.complete(new JsonObject())) {
                askUserMap.remove("ask-timeout");
            }
        });

        assertEquals(1, scheduler.tasks.size());
        scheduler.runAll();
        JsonObject result = future.join();
        assertTrue(result.entrySet().isEmpty());
        assertTrue(askUserMap.isEmpty());
    }

    @Test
    public void planApprovalSafetyNet_timesOutWithRejectPayload() throws Exception {
        ManualSafetyNetScheduler scheduler = new ManualSafetyNetScheduler();
        PermissionHandler handler = new PermissionHandler(createContext(), scheduler);
        Map<String, CompletableFuture<JsonObject>> planApprovalMap = getPlanApprovalMap(handler);
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        planApprovalMap.put("plan-timeout", future);

        handler.scheduleSafetyNet(future, () -> {
            JsonObject timeoutResponse = new JsonObject();
            timeoutResponse.addProperty("approved", false);
            timeoutResponse.addProperty("targetMode", "default");
            timeoutResponse.addProperty("message", "Plan approval timed out");
            if (future.complete(timeoutResponse)) {
                planApprovalMap.remove("plan-timeout");
            }
        });

        assertEquals(1, scheduler.tasks.size());
        scheduler.runAll();
        JsonObject result = future.join();
        assertFalse(result.get("approved").getAsBoolean());
        assertEquals("default", result.get("targetMode").getAsString());
        assertEquals("Plan approval timed out", result.get("message").getAsString());
        assertTrue(planApprovalMap.isEmpty());
    }

    @Test
    public void scheduleSafetyNet_cancelsTaskAfterFutureCompletes() {
        ManualSafetyNetScheduler scheduler = new ManualSafetyNetScheduler();
        PermissionHandler handler = new PermissionHandler(createContext(), scheduler);
        CompletableFuture<Void> future = new CompletableFuture<>();

        handler.scheduleSafetyNet(future, () -> {
        });
        assertEquals(1, scheduler.tasks.size());

        ManualCancellableTask task = scheduler.tasks.get(0);
        assertNotNull(task.runnable);
        assertFalse(task.cancelled);

        future.complete(null);

        assertTrue(task.cancelled);
    }

    @Test
    public void askUserQuestionResponseCompletesAndRemovesPendingRequest() throws Exception {
        PermissionHandler handler = new PermissionHandler(createContext());
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        getAskUserMap(handler).put("ask-1", future);

        invokePrivate(handler, "handleAskUserQuestionResponse",
            "{\"requestId\":\"ask-1\",\"answers\":{\"mode\":\"plan\"}}"
        );

        JsonObject result = future.join();
        assertEquals("plan", result.get("mode").getAsString());
        assertTrue(getAskUserMap(handler).isEmpty());
    }

    @Test
    public void planApprovalResponseCompletesAndRemovesPendingRequest() throws Exception {
        PermissionHandler handler = new PermissionHandler(createContext());
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        getPlanApprovalMap(handler).put("plan-1", future);

        invokePrivate(handler, "handlePlanApprovalResponse",
            "{\"requestId\":\"plan-1\",\"approved\":true,\"targetMode\":\"acceptEdits\"}"
        );

        JsonObject result = future.join();
        assertTrue(result.get("approved").getAsBoolean());
        assertEquals("acceptEdits", result.get("targetMode").getAsString());
        assertTrue(getPlanApprovalMap(handler).isEmpty());
    }

    @Test
    public void deniedPermissionDecisionTriggersDeniedCallback() throws Exception {
        PermissionHandler handler = new PermissionHandler(createContext());
        AtomicBoolean denied = new AtomicBoolean(false);
        handler.setPermissionDeniedCallback(() -> denied.set(true));

        CompletableFuture<Integer> future = new CompletableFuture<>();
        getPermissionMap(handler).put("perm-1", future);

        invokePrivate(handler, "handlePermissionDecision",
            "{\"channelId\":\"perm-1\",\"allow\":false,\"remember\":false}"
        );

        assertEquals(PermissionService.PermissionResponse.DENY.getValue(), future.join().intValue());
        assertTrue(denied.get());
    }

    private static HandlerContext createContext() {
        return new HandlerContext(null, null, null, new HandlerContext.JsCallback() {
            @Override
            public void callJavaScript(String functionName, String... args) {
            }

            @Override
            public String escapeJs(String str) {
                return str;
            }
        });
    }

    private static void invokePrivate(PermissionHandler handler, String methodName, String jsonContent) throws Exception {
        Method method = PermissionHandler.class.getDeclaredMethod(methodName, String.class);
        method.setAccessible(true);
        method.invoke(handler, jsonContent);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, CompletableFuture<Integer>> getPermissionMap(PermissionHandler handler) throws Exception {
        Field field = PermissionHandler.class.getDeclaredField("pendingPermissionRequests");
        field.setAccessible(true);
        return (Map<String, CompletableFuture<Integer>>) field.get(handler);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, CompletableFuture<JsonObject>> getAskUserMap(PermissionHandler handler) throws Exception {
        Field field = PermissionHandler.class.getDeclaredField("pendingAskUserQuestionRequests");
        field.setAccessible(true);
        return (Map<String, CompletableFuture<JsonObject>>) field.get(handler);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, CompletableFuture<JsonObject>> getPlanApprovalMap(PermissionHandler handler) throws Exception {
        Field field = PermissionHandler.class.getDeclaredField("pendingPlanApprovalRequests");
        field.setAccessible(true);
        return (Map<String, CompletableFuture<JsonObject>>) field.get(handler);
    }

    private static final class ManualSafetyNetScheduler implements PermissionHandler.SafetyNetScheduler {

        private final List<ManualCancellableTask> tasks = new ArrayList<>();

        @Override
        public PermissionHandler.CancellableTask schedule(Runnable task, long delaySeconds) {
            ManualCancellableTask cancellableTask = new ManualCancellableTask(task);
            tasks.add(cancellableTask);
            return cancellableTask;
        }

        void runAll() {
            for (ManualCancellableTask task : tasks) {
                task.run();
            }
        }
    }

    private static final class ManualCancellableTask implements PermissionHandler.CancellableTask {

        private final Runnable runnable;
        private boolean cancelled;

        private ManualCancellableTask(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        void run() {
            if (!cancelled) {
                runnable.run();
            }
        }
    }
}
