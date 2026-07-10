package com.protean.copilot.handler;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.protean.copilot.dependency.DependencyManager;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.provider.claude.NodeDetectionResult;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DependencyHandlerTest {

    @Test
    public void getDependencyStatusFailurePushesStableErrorPayloadForAllSdks() {
        RecordingJsCallback callback = new RecordingJsCallback();
        DependencyHandler handler = new DependencyHandler(
            createContext(callback),
            () -> "",
            ignored -> { },
            () -> new NodeDetectionResult("node", "v22.0.0", "10.0.0", true),
            new ThrowingDependencyManager()
        );

        handler.handle("get_dependency_status", "");

        callback.awaitCallCount(1);
        RecordedCall call = callback.lastCall();
        assertEquals("window.updateDependencyStatus", call.functionName);
        JsonObject payload = JsonParser.parseString(call.args[0]).getAsJsonObject();
        assertTrue(payload.has("claude-sdk"));
        assertTrue(payload.has("codex-sdk"));
        assertEquals("error", payload.getAsJsonObject("claude-sdk").get("status").getAsString());
        assertEquals(
            "Dependency status unavailable. status backend exploded.",
            payload.getAsJsonObject("codex-sdk").get("errorMessage").getAsString()
        );
    }

    @Test
    public void installDependencyWithoutNodePushesNodeNotConfiguredResult() {
        RecordingJsCallback callback = new RecordingJsCallback();
        DependencyHandler handler = new DependencyHandler(
            createContext(callback),
            () -> "",
            ignored -> { },
            () -> new NodeDetectionResult("", "", "", false),
            new NodeMissingDependencyManager()
        );

        handler.handle("install_dependency", "{\"id\":\"codex-sdk\"}");

        callback.awaitCallCount(1);
        RecordedCall call = callback.lastCall();
        assertEquals("window.dependencyInstallResult", call.functionName);
        JsonObject payload = JsonParser.parseString(call.args[0]).getAsJsonObject();
        assertEquals(false, payload.get("success").getAsBoolean());
        assertEquals("codex-sdk", payload.get("sdkId").getAsString());
        assertEquals("node_not_configured", payload.get("error").getAsString());
        assertEquals(
            "Node.js is not configured. Please set the Node.js path in Basic Settings first.",
            payload.get("message").getAsString()
        );
    }

    @Test
    public void installDependencyFailureNormalizesPermissionDeniedMessage() {
        RecordingJsCallback callback = new RecordingJsCallback();
        DependencyHandler handler = new DependencyHandler(
            createContext(callback),
            () -> "",
            ignored -> { },
            () -> new NodeDetectionResult("node", "v22.0.0", "10.0.0", true),
            new PermissionDeniedDependencyManager()
        );

        handler.handle("install_dependency", "{\"id\":\"codex-sdk\"}");

        callback.awaitCallCount(1);
        RecordedCall call = callback.lastCall();
        assertEquals("window.dependencyInstallResult", call.functionName);
        JsonObject payload = JsonParser.parseString(call.args[0]).getAsJsonObject();
        assertEquals("approval denied for apply_patch", payload.get("error").getAsString());
        assertEquals(
            "Codex SDK permission request was denied. approval denied for apply_patch.",
            payload.get("message").getAsString()
        );
    }

    @Test
    public void checkNodeEnvironmentPushesStatusToBothCallbacks() {
        RecordingJsCallback callback = new RecordingJsCallback();
        DependencyHandler handler = new DependencyHandler(
            createContext(callback),
            () -> "",
            ignored -> { },
            () -> new NodeDetectionResult("/usr/local/bin/node", "v22.0.0", "10.8.0", true),
            new NodeMissingDependencyManager()
        );

        handler.handle("check_node_environment", "");

        callback.awaitCallCount(2);
        assertEquals("window.nodeEnvironmentStatus", callback.calls.get(0).functionName);
        assertEquals("window.updateNodeEnvironment", callback.calls.get(1).functionName);
        JsonObject payload = JsonParser.parseString(callback.calls.get(0).args[0]).getAsJsonObject();
        assertTrue(payload.get("available").getAsBoolean());
        assertEquals("/usr/local/bin/node", payload.get("nodePath").getAsString());
    }

    private static HandlerContext createContext(RecordingJsCallback callback) {
        return new HandlerContext(null, null, null, callback);
    }

    private static final class ThrowingDependencyManager extends DependencyManager {
        ThrowingDependencyManager() {
            super(null);
        }

        @Override
        public JsonObject getAllSdkStatus() {
            throw new IllegalStateException("status backend exploded");
        }
    }

    private static final class NodeMissingDependencyManager extends DependencyManager {
        NodeMissingDependencyManager() {
            super(null);
        }

        @Override
        public boolean checkNodeEnvironment() {
            return false;
        }
    }

    private static final class PermissionDeniedDependencyManager extends DependencyManager {
        PermissionDeniedDependencyManager() {
            super(null);
        }

        @Override
        public boolean checkNodeEnvironment() {
            return true;
        }

        @Override
        public com.protean.copilot.dependency.InstallResult installSdkSync(
            String sdkId,
            String requestedVersion,
            java.util.function.Consumer<String> logCallback
        ) {
            return com.protean.copilot.dependency.InstallResult.failure(
                sdkId,
                requestedVersion,
                "approval denied for apply_patch",
                ""
            );
        }
    }

    private static final class RecordingJsCallback implements HandlerContext.JsCallback {
        private final List<RecordedCall> calls = new ArrayList<>();

        @Override
        public synchronized void callJavaScript(String functionName, String... args) {
            calls.add(new RecordedCall(functionName, args));
            notifyAll();
        }

        @Override
        public String escapeJs(String str) {
            return str;
        }

        synchronized void awaitCallCount(int expectedCount) {
            long deadline = System.currentTimeMillis() + 3000;
            while (calls.size() < expectedCount && System.currentTimeMillis() < deadline) {
                try {
                    wait(50L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError("Interrupted while waiting for JS callback", e);
                }
            }
            if (calls.size() < expectedCount) {
                throw new AssertionError("Expected at least " + expectedCount + " JS calls but got " + calls.size());
            }
        }

        synchronized RecordedCall lastCall() {
            return calls.get(calls.size() - 1);
        }
    }

    private record RecordedCall(String functionName, String[] args) {
    }
}
