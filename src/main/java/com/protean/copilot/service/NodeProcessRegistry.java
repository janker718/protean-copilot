package com.protean.copilot.service;

import com.protean.copilot.provider.common.BaseSDKBridge;
import com.protean.copilot.settings.SettingsService;
import com.protean.copilot.ui.toolwindow.ProteanChatWindow;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Project-scoped registry for the Node Process Management panel.
 *
 * <p>Compared with jetbrains-cc-gui, Protean currently has a simpler runtime:
 * one bridge process per provider rather than a daemon + per-channel process manager.
 * This registry therefore surfaces live bridge processes as {@code DAEMON} entries,
 * while keeping the same JSON-facing structure so the webview layer remains aligned.
 */
@Service(Service.Level.PROJECT)
public final class NodeProcessRegistry implements Disposable {

    private static final Logger LOG = Logger.getInstance(NodeProcessRegistry.class);

    private static final String[] OWNED_PROCESS_HINTS = {
        "claude-sdk-bridge.mjs",
        "codex-sdk-bridge.mjs"
    };

    private final Project project;

    public NodeProcessRegistry(@NotNull Project project) {
        this.project = project;
    }

    public static NodeProcessRegistry getInstance(@NotNull Project project) {
        return project.getService(NodeProcessRegistry.class);
    }

    public List<NodeProcessInfo> snapshot() {
        long now = System.currentTimeMillis();
        List<NodeProcessInfo> result = new ArrayList<>();
        Set<Long> knownPids = new HashSet<>();

        for (ProteanChatWindow window : ProteanChatWindow.getAllChatWindowsForProject(project)) {
            if (window == null || window.isDisposed()) {
                continue;
            }
            String tabName = resolveTabName(window);
            String sessionId = safeSessionId(window);
            addBridgeProcess(result, knownPids, now, window.getClaudeSDKBridge(), "claude", sessionId, tabName);
            addBridgeProcess(result, knownPids, now, window.getCodexSDKBridge(), "codex", sessionId, tabName);
        }

        scanOrphans(result, knownPids, now);
        return result;
    }

    public boolean killByPid(long pid) {
        if (pid <= 0 || !isPidOwned(pid, snapshotPids())) {
            return false;
        }
        return terminateTrackedPid(pid);
    }

    public boolean restartDaemonByPid(long pid) {
        for (ProteanChatWindow window : ProteanChatWindow.getAllChatWindowsForProject(project)) {
            if (window == null || window.isDisposed()) {
                continue;
            }
            if (restartBridgeIfMatches(window.getClaudeSDKBridge(), pid)) {
                return true;
            }
            if (restartBridgeIfMatches(window.getCodexSDKBridge(), pid)) {
                return true;
            }
        }
        return killByPid(pid);
    }

    public int killAllOrphans() {
        int killed = 0;
        for (NodeProcessInfo info : snapshot()) {
            if (info.getKind() == NodeProcessInfo.Kind.ORPHAN && terminateTrackedPid(info.getPid())) {
                killed++;
            }
        }
        return killed;
    }

    static boolean isPidOwned(long pid, Set<Long> ownedPids) {
        return pid > 0 && ownedPids != null && ownedPids.contains(pid);
    }

    static boolean looksLikeOurProcess(String fingerprint) {
        if (fingerprint == null) {
            return false;
        }
        String lower = fingerprint.toLowerCase();
        for (String hint : OWNED_PROCESS_HINTS) {
            if (lower.contains(hint.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    static @Nullable String detectProviderFromCmd(String cmd) {
        if (cmd == null) {
            return null;
        }
        String lower = cmd.toLowerCase();
        if (lower.contains("codex")) {
            return "codex";
        }
        if (lower.contains("claude")) {
            return "claude";
        }
        return null;
    }

    private void addBridgeProcess(
        List<NodeProcessInfo> result,
        Set<Long> knownPids,
        long now,
        BaseSDKBridge bridge,
        String provider,
        String sessionId,
        String tabName
    ) {
        if (bridge == null) {
            return;
        }
        Process process = bridge.getNodeProcessForInspection();
        if (process == null || !process.isAlive()) {
            return;
        }

        long pid = process.pid();
        knownPids.add(pid);
        ProcessHandle.Info info = safeInfo(process);
        long startedAt = info != null ? info.startInstant().map(Instant::toEpochMilli).orElse(-1L) : -1L;

        result.add(NodeProcessInfo.builder()
            .kind(NodeProcessInfo.Kind.DAEMON)
            .provider(provider)
            .pid(pid)
            .alive(true)
            .startedAtMs(startedAt)
            .uptimeMs(startedAt > 0 ? Math.max(0L, now - startedAt) : 0L)
            .command(extractCommand(info))
            .activeRequestCount(bridge.getActiveRequestCountForInspection())
            .sessionId(sessionId)
            .tabName(tabName)
            .build());
    }

    private void scanOrphans(List<NodeProcessInfo> result, Set<Long> knownPids, long now) {
        final long currentJvmPid;
        try {
            currentJvmPid = ProcessHandle.current().pid();
        } catch (Exception e) {
            LOG.warn("[NodeProcessRegistry] Cannot resolve current JVM PID, skipping orphan scan: " + e.getMessage());
            return;
        }

        try {
            ProcessHandle.allProcesses().forEach(handle -> {
                long pid = handle.pid();
                if (knownPids.contains(pid)) {
                    return;
                }
                String fingerprint = extractCommand(handle.info());
                if (!looksLikeOurProcess(fingerprint)) {
                    return;
                }
                long parentPid = handle.parent().map(ProcessHandle::pid).orElse(-1L);
                if (parentPid != currentJvmPid) {
                    return;
                }
                long startedAt = handle.info().startInstant().map(Instant::toEpochMilli).orElse(-1L);
                result.add(NodeProcessInfo.builder()
                    .kind(NodeProcessInfo.Kind.ORPHAN)
                    .provider(detectProviderFromCmd(fingerprint))
                    .pid(pid)
                    .alive(handle.isAlive())
                    .startedAtMs(startedAt)
                    .uptimeMs(startedAt > 0 ? Math.max(0L, now - startedAt) : 0L)
                    .command(fingerprint)
                    .build());
            });
        } catch (Exception e) {
            LOG.warn("[NodeProcessRegistry] Orphan scan failed: " + e.getMessage());
        }
    }

    private Set<Long> snapshotPids() {
        Set<Long> pids = new HashSet<>();
        for (NodeProcessInfo info : snapshot()) {
            pids.add(info.getPid());
        }
        return pids;
    }

    private boolean restartBridgeIfMatches(BaseSDKBridge bridge, long pid) {
        if (bridge == null) {
            return false;
        }
        Process process = bridge.getNodeProcessForInspection();
        if (process == null || !process.isAlive() || process.pid() != pid) {
            return false;
        }
        try {
            bridge.shutdown();
            bridge.start(new SettingsService().getNodePath());
            return true;
        } catch (Exception e) {
            LOG.warn("[NodeProcessRegistry] Failed to restart bridge pid=" + pid + ": " + e.getMessage(), e);
            return false;
        }
    }

    private boolean terminateTrackedPid(long pid) {
        Optional<ProcessHandle> handleOptional = ProcessHandle.of(pid);
        if (handleOptional.isEmpty()) {
            return false;
        }
        ProcessHandle handle = handleOptional.get();
        boolean destroyed = handle.destroy();
        if (!destroyed && handle.isAlive()) {
            destroyed = handle.destroyForcibly();
        }
        return destroyed;
    }

    private static @Nullable ProcessHandle.Info safeInfo(Process process) {
        try {
            return process.toHandle().info();
        } catch (Exception e) {
            return null;
        }
    }

    private static @Nullable String extractCommand(@Nullable ProcessHandle.Info info) {
        if (info == null) {
            return null;
        }
        Optional<String> commandLine = info.commandLine();
        if (commandLine.isPresent()) {
            return commandLine.get();
        }
        return info.command().orElse(null);
    }

    private static @Nullable String resolveTabName(ProteanChatWindow window) {
        try {
            Content content = window.getParentContent();
            if (content == null) {
                return null;
            }
            String displayName = content.getDisplayName();
            return displayName != null && !displayName.isBlank() ? displayName : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static @Nullable String safeSessionId(ProteanChatWindow window) {
        try {
            String sessionId = window.getSessionId();
            return sessionId != null && !sessionId.isBlank() ? sessionId : null;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void dispose() {
        // Pure aggregator; owned processes are disposed by their bridges.
    }
}
