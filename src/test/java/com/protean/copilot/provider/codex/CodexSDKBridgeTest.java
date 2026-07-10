package com.protean.copilot.provider.codex;

import com.google.gson.JsonObject;
import com.protean.copilot.settings.CodemossSettingsService;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class CodexSDKBridgeTest {

    @Test
    public void mapsPlanModeToReadOnlySandboxAndUntrustedApproval() {
        assertEquals("read-only", CodexSDKBridge.resolveSandboxMode("plan", null));
        assertEquals("untrusted", CodexSDKBridge.resolveApprovalPolicy("plan"));
    }

    @Test
    public void mapsAcceptEditsToWorkspaceWriteAndOnRequestApproval() {
        assertEquals("workspace-write", CodexSDKBridge.resolveSandboxMode("acceptEdits", null));
        assertEquals("on-request", CodexSDKBridge.resolveApprovalPolicy("acceptEdits"));
    }

    @Test
    public void keepsConfiguredSandboxForBypassPermissions() {
        assertEquals("workspace-write", CodexSDKBridge.resolveSandboxMode("bypassPermissions", "workspace-write"));
        assertEquals("never", CodexSDKBridge.resolveApprovalPolicy("bypassPermissions"));
    }

    @Test
    public void defaultsNormalModeToWorkspaceWrite() {
        assertEquals("workspace-write", CodexSDKBridge.resolveSandboxMode("default", null));
        assertEquals("untrusted", CodexSDKBridge.resolveApprovalPolicy("default"));
    }

    @Test
    public void exposesManagedCodexSdkPathToTheBridge() throws Exception {
        Map<String, String> environment = createBridge().getBridgeEnvironment();

        assertEquals(
            Path.of(System.getProperty("user.home"), ".codemoss", "dependencies", "codex-sdk", "node_modules").toString(),
            environment.get("PROTEAN_CODEX_SDK_NODE_MODULES")
        );
    }

    @Test
    public void buildQueryMessageCarriesSandboxApprovalAndReasoning() throws Exception {
        CodexSDKBridge bridge = createBridge();
        JsonObject message = invokeBuildQueryMessage(
            bridge,
            "request-1",
            "Summarize the diff",
            "/workspace",
            "gpt-5.5",
            "acceptEdits",
            "high"
        );

        assertEquals("query", message.get("type").getAsString());
        assertEquals("acceptEdits", message.get("permissionMode").getAsString());
        assertEquals("workspace-write", message.get("sandboxMode").getAsString());
        assertEquals("on-request", message.get("approvalPolicy").getAsString());
        assertEquals("high", message.get("reasoningEffort").getAsString());
        assertEquals("/workspace", message.get("workingDirectory").getAsString());
    }

    @Test
    public void buildResumeMessagePreservesRequestedPermissionOptions() throws Exception {
        CodexSDKBridge bridge = createBridge();
        JsonObject message = invokeBuildResumeMessage(bridge, "thread-1", "continue", "/workspace", "acceptEdits");

        assertEquals("resume", message.get("type").getAsString());
        assertEquals("acceptEdits", message.get("permissionMode").getAsString());
        assertEquals("workspace-write", message.get("sandboxMode").getAsString());
        assertEquals("on-request", message.get("approvalPolicy").getAsString());
        assertEquals("/workspace", message.get("workingDirectory").getAsString());
    }

    @Test
    public void buildResumeMessageSupportsReadOnlySandboxForPlanMode() throws Exception {
        JsonObject message = invokeBuildResumeMessage(createBridge(), "thread-1", "continue", "/workspace", "plan");

        assertEquals("plan", message.get("permissionMode").getAsString());
        assertEquals("read-only", message.get("sandboxMode").getAsString());
        assertEquals("untrusted", message.get("approvalPolicy").getAsString());
    }

    private static CodexSDKBridge createBridge() throws Exception {
        CodexSDKBridge bridge = (CodexSDKBridge) getUnsafe().allocateInstance(CodexSDKBridge.class);
        Field settingsField = CodexSDKBridge.class.getDeclaredField("settingsService");
        settingsField.setAccessible(true);
        settingsField.set(bridge, getUnsafe().allocateInstance(TestSettingsService.class));
        return bridge;
    }

    private static JsonObject invokeBuildQueryMessage(
        CodexSDKBridge bridge,
        String sessionId,
        String prompt,
        String cwd,
        String model,
        String permissionMode,
        String reasoningEffort
    ) throws Exception {
        Method method = CodexSDKBridge.class.getDeclaredMethod(
            "buildQueryMessage",
            String.class,
            String.class,
            String.class,
            String.class,
            String.class,
            String.class
        );
        method.setAccessible(true);
        return (JsonObject) method.invoke(bridge, sessionId, prompt, cwd, model, permissionMode, reasoningEffort);
    }

    private static JsonObject invokeBuildResumeMessage(
        CodexSDKBridge bridge,
        String sessionId,
        String prompt,
        String cwd,
        String permissionMode
    ) throws Exception {
        Method method = CodexSDKBridge.class.getDeclaredMethod(
            "buildResumeMessage",
            String.class,
            String.class,
            String.class,
            String.class
        );
        method.setAccessible(true);
        return (JsonObject) method.invoke(bridge, sessionId, prompt, cwd, permissionMode);
    }

    private static final class TestSettingsService extends CodemossSettingsService {
        @Override
        public String getCodexSandboxMode(String projectPath) {
            return "workspace-write";
        }

        @Override
        public String getCodexRuntimeAccessMode() {
            return CODEX_RUNTIME_ACCESS_MANAGED;
        }
    }

    private static sun.misc.Unsafe getUnsafe() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
    }
}
