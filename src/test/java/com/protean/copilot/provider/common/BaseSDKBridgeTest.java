package com.protean.copilot.provider.common;

import com.google.gson.JsonObject;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BaseSDKBridgeTest {

    @Test
    public void remapsPendingRequestSessionToActualThreadSession() throws Exception {
        TestBridge bridge = new TestBridge();
        Map<String, BaseSDKBridge.SessionState> sessions = bridge.activeSessions();
        sessions.put("request-1", new BaseSDKBridge.SessionState("request-1", "/tmp/project"));

        bridge.remap("request-1", "thread-1", "/tmp/project");

        assertFalse(sessions.containsKey("request-1"));
        assertTrue(sessions.containsKey("thread-1"));
        assertEquals("thread-1", sessions.get("thread-1").sessionId);
    }

    @Test
    public void createsActualSessionWhenRequestAliasIsMissing() throws Exception {
        TestBridge bridge = new TestBridge();

        bridge.remap("missing-request", "thread-2", "/tmp/project");

        BaseSDKBridge.SessionState state = bridge.activeSessions().get("thread-2");
        assertNotNull(state);
        assertEquals("thread-2", state.sessionId);
    }

    @Test
    public void reportsUnavailableSdkWithActionableCallbackMessage() {
        TestBridge bridge = new TestBridge();
        List<String> events = new ArrayList<>();
        bridge.setCallback((functionName, args) -> events.add(functionName + ":" + String.join("|", args)));

        JsonObject ready = new JsonObject();
        ready.addProperty("version", "0.143.0");
        ready.addProperty("sdkAvailable", false);
        ready.addProperty("runtime", "node v22.0.0 on darwin");
        ready.addProperty("sdkPackage", "@openai/codex-sdk");
        ready.addProperty("hint", "Install the locked dependency before retrying.");

        bridge.handleReady(ready);

        assertTrue(events.contains("updateStatus:Test runtime not ready"));
        assertTrue(events.contains("addErrorMessage:Test runtime unavailable (version 0.143.0). Install the locked dependency before retrying."));
        assertTrue(events.contains("onBridgeReady:0.143.0|false"));
    }

    @Test
    public void completesOnlyTargetSessionWhenErrorCarriesSessionId() throws Exception {
        TestBridge bridge = new TestBridge();
        Map<String, BaseSDKBridge.SessionState> sessions = bridge.activeSessions();
        BaseSDKBridge.SessionState failed = new BaseSDKBridge.SessionState("thread-1", "/tmp/project");
        failed.streaming = true;
        failed.responseFuture = new CompletableFuture<>();
        BaseSDKBridge.SessionState healthy = new BaseSDKBridge.SessionState("thread-2", "/tmp/project");
        healthy.streaming = true;
        healthy.responseFuture = new CompletableFuture<>();
        sessions.put("thread-1", failed);
        sessions.put("thread-2", healthy);

        JsonObject error = new JsonObject();
        error.addProperty("message", "Sandbox denied write access");
        error.addProperty("code", "SANDBOX_DENIED");
        error.addProperty("sessionId", "thread-1");
        error.addProperty("phase", "query");
        error.addProperty("hint", "Retry with workspace-write or request approval.");

        bridge.invokeError(error);

        assertTrue(failed.responseFuture.isCompletedExceptionally());
        assertFalse(healthy.responseFuture.isDone());
    }

    @Test
    public void formatsPermissionErrorsForFrontendDiagnostics() throws Exception {
        TestBridge bridge = new TestBridge();
        List<String> events = new ArrayList<>();
        bridge.setCallback((functionName, args) -> events.add(functionName + ":" + String.join("|", args)));

        JsonObject error = new JsonObject();
        error.addProperty("message", "approval denied for apply_patch");
        error.addProperty("code", "PERMISSION_DENIED");
        error.addProperty("phase", "query");
        error.addProperty("hint", "Retry after requesting approval.");

        bridge.invokeError(error);

        assertTrue(events.contains(
            "addErrorMessage:Test permission request was denied. approval denied for apply_patch. Retry after requesting approval."
        ));
        assertTrue(events.contains(
            "updateStatus:Test permission request was denied. approval denied for apply_patch. Retry after requesting approval."
        ));
    }

    @Test
    public void resumeSessionPassesPermissionModeThroughProviderMessageBuilder() {
        TestBridge bridge = new TestBridge();

        bridge.resumeSession("thread-1", "continue", "/workspace", "acceptEdits");

        assertNotNull(bridge.lastWrittenMessage);
        assertEquals("resume", bridge.lastWrittenMessage.get("type").getAsString());
        assertEquals("acceptEdits", bridge.lastWrittenMessage.get("permissionMode").getAsString());
    }

    private static final class TestBridge extends BaseSDKBridge {

        private JsonObject lastWrittenMessage;

        @Override
        protected String getProviderName() {
            return "Test";
        }

        @Override
        protected String getDefaultModel() {
            return "test-model";
        }

        @Override
        protected String getBridgeScriptResource() {
            return "bridge/test.mjs";
        }

        @Override
        public boolean isRunning() {
            return true;
        }

        @Override
        protected void writeMessage(JsonObject message) {
            lastWrittenMessage = message;
        }

        @Override
        protected JsonObject buildResumeMessage(String sessionId, String prompt, String cwd, String permissionMode) {
            JsonObject message = super.buildResumeMessage(sessionId, prompt, cwd, permissionMode);
            message.addProperty("permissionMode", permissionMode);
            return message;
        }

        void remap(String requestSessionId, String actualSessionId, String cwd) {
            remapSessionState(requestSessionId, actualSessionId, cwd);
        }

        void invokeError(JsonObject error) throws Exception {
            Method method = BaseSDKBridge.class.getDeclaredMethod("handleError", JsonObject.class);
            method.setAccessible(true);
            method.invoke(this, error);
        }

        @SuppressWarnings("unchecked")
        Map<String, BaseSDKBridge.SessionState> activeSessions() throws Exception {
            Field field = BaseSDKBridge.class.getDeclaredField("activeSessions");
            field.setAccessible(true);
            return (Map<String, BaseSDKBridge.SessionState>) field.get(this);
        }
    }
}
