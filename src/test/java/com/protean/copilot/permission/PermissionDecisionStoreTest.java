package com.protean.copilot.permission;

import com.google.gson.JsonObject;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class PermissionDecisionStoreTest {

    @Test
    public void writeMemoryKeyIgnoresContentAndKeepsFilePathScope() {
        PermissionDecisionStore store = new PermissionDecisionStore();

        JsonObject first = new JsonObject();
        first.addProperty("file_path", "/repo/src/App.java");
        first.addProperty("content", "alpha");

        JsonObject second = new JsonObject();
        second.addProperty("file_path", "/repo/src/App.java");
        second.addProperty("content", "beta");

        assertEquals(store.buildMemoryKey("Write", first), store.buildMemoryKey("Write", second));
    }

    @Test
    public void writeMemoryKeySeparatesDifferentTargetFiles() {
        PermissionDecisionStore store = new PermissionDecisionStore();

        JsonObject first = new JsonObject();
        first.addProperty("file_path", "/repo/src/App.java");
        first.addProperty("content", "alpha");

        JsonObject second = new JsonObject();
        second.addProperty("file_path", "/repo/src/Other.java");
        second.addProperty("content", "alpha");

        assertNotEquals(store.buildMemoryKey("Write", first), store.buildMemoryKey("Write", second));
    }

    @Test
    public void commandMemoryKeyTracksCommandAndWorkingDirectory() {
        PermissionDecisionStore store = new PermissionDecisionStore();

        Map<String, Object> first = new LinkedHashMap<>();
        first.put("command", "npm test");
        first.put("cwd", "/repo");

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("command", "npm test");
        second.put("cwd", "/repo");
        second.put("timeout", 60);

        Map<String, Object> third = new LinkedHashMap<>();
        third.put("command", "npm test");
        third.put("cwd", "/repo/subdir");

        assertEquals(store.buildMemoryKey("Bash", first), store.buildMemoryKey("Bash", second));
        assertNotEquals(store.buildMemoryKey("Bash", first), store.buildMemoryKey("Bash", third));
    }

    @Test
    public void managerConsumesRememberedParameterDecisionForLocalPermissionRequests() {
        PermissionDecisionStore store = new PermissionDecisionStore();
        PermissionManager manager = new PermissionManager(store);

        Map<String, Object> first = new LinkedHashMap<>();
        first.put("file_path", "/repo/src/App.java");
        first.put("content", "alpha");
        store.rememberParameterDecision("Write", first, PermissionService.PermissionResponse.ALLOW_ALWAYS);

        Map<String, Object> second = new LinkedHashMap<>();
        second.put("file_path", "/repo/src/App.java");
        second.put("content", "beta");

        PermissionRequest request = manager.createRequest("channel-1", "Write", second, null, null);

        assertEquals(
            PermissionRequest.PermissionResult.Behavior.ALLOW,
            request.getResultFuture().join().getBehavior()
        );
    }
}
