package com.protean.copilot.session;

import com.intellij.openapi.project.Project;
import com.protean.copilot.bridge.SdkBridge;
import com.protean.copilot.provider.claude.ClaudeSDKBridge;
import com.protean.copilot.provider.codex.CodexSDKBridge;
import com.protean.copilot.settings.CodemossSettingsService;
import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThrows;

public class SessionSendServiceResumeTest {

    @Test
    public void codexHistoryResumeUsesResumeSessionAndClearsFlag() throws Exception {
        TestCodexBridge codexBridge = allocate(TestCodexBridge.class);
        SdkBridge sdkBridge = new SdkBridge();
        sdkBridge.setCodexBridge(codexBridge);

        SessionSendService service = new SessionSendService(
            null,
            sdkBridge,
            new InMemoryPermissionModeStore(),
            () -> "/workspace",
            new TestCodemossSettingsService(CodemossSettingsService.CODEX_RUNTIME_ACCESS_MANAGED)
        );
        ChatSession session = createSession("codex", "thread-1", "/workspace", "acceptEdits", "gpt-5.5", true);

        service.sendMessageToProvider(null, session, "continue", "acceptEdits", "high").join();

        assertEquals(1, codexBridge.resumeCalls);
        assertEquals(0, codexBridge.queryCalls);
        assertFalse(session.requiresProviderResume());
        assertEquals("thread-1", codexBridge.lastResumeSessionId);
        assertEquals("continue", codexBridge.lastResumePrompt);
        assertEquals("/workspace", codexBridge.lastResumeCwd);
        assertEquals("acceptEdits", codexBridge.lastResumePermissionMode);
    }

    @Test
    public void claudeHistoryResumeUsesResumeSessionAndClearsFlag() throws Exception {
        TestClaudeBridge claudeBridge = allocate(TestClaudeBridge.class);
        SdkBridge sdkBridge = new SdkBridge();
        sdkBridge.setClaudeBridge(claudeBridge);

        SessionSendService service = new SessionSendService(
            null,
            sdkBridge,
            new InMemoryPermissionModeStore(),
            () -> "/workspace",
            new TestCodemossSettingsService(CodemossSettingsService.CODEX_RUNTIME_ACCESS_MANAGED)
        );
        ChatSession session = createSession("claude", "session-1", "/workspace", "plan", "claude-sonnet-4-6", true);

        service.sendMessageToProvider(null, session, "continue", "plan", null).join();

        assertEquals(1, claudeBridge.resumeCalls);
        assertEquals(0, claudeBridge.queryCalls);
        assertFalse(session.requiresProviderResume());
    }

    @Test
    public void codexSendFailsFastWhenRuntimeAccessIsInactive() throws Exception {
        TestCodexBridge codexBridge = allocate(TestCodexBridge.class);
        SdkBridge sdkBridge = new SdkBridge();
        sdkBridge.setCodexBridge(codexBridge);

        SessionSendService service = new SessionSendService(
            null,
            sdkBridge,
            new InMemoryPermissionModeStore(),
            () -> "/workspace",
            new TestCodemossSettingsService(CodemossSettingsService.CODEX_RUNTIME_ACCESS_INACTIVE)
        );
        ChatSession session = createSession("codex", "thread-1", "/workspace", "acceptEdits", "gpt-5.5", false);

        CompletionException ex = assertThrows(
            CompletionException.class,
            () -> service.sendMessageToProvider(null, session, "continue", "acceptEdits", "high").join()
        );

        assertTrue(ex.getCause().getMessage().contains("Codex local configuration access is not authorized"));
        assertEquals(0, codexBridge.queryCalls);
        assertEquals(0, codexBridge.resumeCalls);
    }

    private static ChatSession createSession(
        String provider,
        String sessionId,
        String cwd,
        String permissionMode,
        String model,
        boolean requiresResume
    ) throws Exception {
        ChatSession session = (ChatSession) getUnsafe().allocateInstance(ChatSession.class);
        setField(session, "project", (Project) null);
        setField(session, "sdkBridge", new SdkBridge());
        setField(session, "sendService", null);
        setField(session, "providerRouter", null);
        setField(session, "messageOrchestrator", null);
        setField(session, "messages", new java.util.ArrayList<ChatSession.Message>());
        setField(session, "provider", provider);
        setField(session, "sessionId", sessionId);
        setField(session, "cwd", cwd);
        setField(session, "permissionMode", permissionMode);
        setField(session, "model", model);
        setField(session, "providerResumeRequired", requiresResume);
        setField(session, "lastModifiedTime", System.currentTimeMillis());
        return session;
    }

    private static void setField(ChatSession session, String fieldName, Object value) throws Exception {
        Field field = ChatSession.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(session, value);
    }

    private static sun.misc.Unsafe getUnsafe() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (sun.misc.Unsafe) field.get(null);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(getUnsafe().allocateInstance(type));
    }

    private static final class TestClaudeBridge extends ClaudeSDKBridge {
        int queryCalls;
        int resumeCalls;

        @Override
        public boolean isRunning() {
            return true;
        }

        @Override
        public CompletableFuture<Void> query(
            String sessionId,
            String prompt,
            String cwd,
            String model,
            String permissionMode,
            String reasoningEffort
        ) {
            queryCalls++;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> resumeSession(String sessionId, String prompt, String cwd, String permissionMode) {
            resumeCalls++;
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class InMemoryPermissionModeStore implements SessionSendService.PermissionModeStore {
        private String permissionMode = "bypassPermissions";

        @Override
        public void setPermissionMode(String mode) {
            permissionMode = mode;
        }
    }

    private static final class TestCodexBridge extends CodexSDKBridge {
        int queryCalls;
        int resumeCalls;
        String lastResumeSessionId;
        String lastResumePrompt;
        String lastResumeCwd;
        String lastResumePermissionMode;

        @Override
        public boolean isRunning() {
            return true;
        }

        @Override
        public CompletableFuture<Void> query(
            String sessionId,
            String prompt,
            String cwd,
            String model,
            String permissionMode,
            String reasoningEffort
        ) {
            queryCalls++;
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletableFuture<Void> resumeSession(String sessionId, String prompt, String cwd, String permissionMode) {
            resumeCalls++;
            lastResumeSessionId = sessionId;
            lastResumePrompt = prompt;
            lastResumeCwd = cwd;
            lastResumePermissionMode = permissionMode;
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class TestCodemossSettingsService extends CodemossSettingsService {
        private final String runtimeAccessMode;

        private TestCodemossSettingsService(String runtimeAccessMode) throws Exception {
            super(Files.createTempDirectory("codemoss-runtime-access-test"));
            this.runtimeAccessMode = runtimeAccessMode;
        }

        @Override
        public String getCodexRuntimeAccessMode() {
            return runtimeAccessMode;
        }
    }
}
