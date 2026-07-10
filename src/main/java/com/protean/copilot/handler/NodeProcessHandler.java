package com.protean.copilot.handler;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.protean.copilot.handler.core.BaseMessageHandler;
import com.protean.copilot.handler.core.HandlerContext;
import com.protean.copilot.service.NodeProcessInfo;
import com.protean.copilot.service.NodeProcessRegistry;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Node process management handler aligned with jetbrains-cc-gui's frontend protocol.
 */
public final class NodeProcessHandler extends BaseMessageHandler {

    private static final Logger LOG = Logger.getInstance(NodeProcessHandler.class);
    private static final List<String> SUPPORTED_TYPES = List.of(
        "get_node_processes",
        "kill_node_process",
        "kill_all_orphans",
        "restart_node_daemon"
    );
    private static final long KILL_REFRESH_DELAY_MS = 200L;
    private static final long RESTART_REFRESH_DELAY_MS = 500L;

    private final Gson gson = new Gson();
    private final Operations operations;
    private final Executor asyncExecutor;
    private final Scheduler scheduler;
    private final JsDispatcher jsDispatcher;

    interface Operations {
        List<NodeProcessInfo> snapshot();
        boolean killByPid(long pid);
        int killAllOrphans();
        boolean restartDaemonByPid(long pid);
    }

    interface Scheduler {
        void schedule(Runnable task, long delayMs);
    }

    interface JsDispatcher {
        void dispatch(String functionName, String json);
    }

    public NodeProcessHandler(HandlerContext context) {
        this(
            context,
            new RegistryOperations(defaultRegistrySupplier(context)),
            AppExecutorUtil.getAppExecutorService(),
            (task, delayMs) -> AppExecutorUtil.getAppScheduledExecutorService().schedule(task, delayMs, TimeUnit.MILLISECONDS),
            (functionName, json) -> ApplicationManager.getApplication().invokeLater(() ->
                context.callJavaScript(functionName, context.escapeJs(json))
            )
        );
    }

    NodeProcessHandler(
        HandlerContext context,
        Operations operations,
        Executor asyncExecutor,
        Scheduler scheduler,
        JsDispatcher jsDispatcher
    ) {
        super(context);
        this.operations = operations;
        this.asyncExecutor = asyncExecutor;
        this.scheduler = scheduler;
        this.jsDispatcher = jsDispatcher;
    }

    @Override
    public List<String> getSupportedTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public boolean handle(String type, String content) {
        switch (type) {
            case "get_node_processes" -> handleGetNodeProcesses();
            case "kill_node_process" -> handleKillNodeProcess(content);
            case "kill_all_orphans" -> handleKillAllOrphans();
            case "restart_node_daemon" -> handleRestartDaemon(content);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void handleGetNodeProcesses() {
        runAsync(() -> {
            try {
                List<NodeProcessInfo> processes = operations().snapshot();
                pushUpdate(buildProcessListJson(processes));
            } catch (Exception e) {
                LOG.warn("[NodeProcessHandler] get_node_processes failed: " + e.getMessage(), e);
                pushUpdate(buildProcessListJson(Collections.emptyList()));
            }
        });
    }

    private void handleKillNodeProcess(String rawContent) {
        runAsync(() -> {
            long pid = -1L;
            String reportedId = null;
            try {
                JsonObject payload = gson.fromJson(rawContent, JsonObject.class);
                if (payload != null) {
                    if (payload.has("pid") && !payload.get("pid").isJsonNull()) {
                        pid = payload.get("pid").getAsLong();
                    }
                    if (payload.has("id") && !payload.get("id").isJsonNull()) {
                        reportedId = payload.get("id").getAsString();
                    }
                }
            } catch (Exception e) {
                LOG.warn("[NodeProcessHandler] kill_node_process bad payload: " + e.getMessage());
            }

            boolean success = false;
            String error = null;
            if (pid > 0) {
                try {
                    success = operations().killByPid(pid);
                } catch (Exception e) {
                    error = e.getMessage();
                }
            } else {
                error = "Invalid or missing PID";
            }

            JsonObject result = new JsonObject();
            result.addProperty("pid", pid);
            if (reportedId != null) {
                result.addProperty("id", reportedId);
            }
            result.addProperty("success", success);
            if (error != null) {
                result.addProperty("error", error);
            }
            pushKillResult(gson.toJson(result));
            scheduleRefresh(KILL_REFRESH_DELAY_MS);
        });
    }

    private void handleKillAllOrphans() {
        runAsync(() -> {
            int killed = 0;
            String error = null;
            try {
                killed = operations().killAllOrphans();
            } catch (Exception e) {
                error = e.getMessage();
                LOG.warn("[NodeProcessHandler] kill_all_orphans failed: " + e.getMessage(), e);
            }

            JsonObject result = new JsonObject();
            result.addProperty("killed", killed);
            if (error != null) {
                result.addProperty("error", error);
            }
            pushKillResult(gson.toJson(result));
            scheduleRefresh(KILL_REFRESH_DELAY_MS);
        });
    }

    private void handleRestartDaemon(String rawContent) {
        runAsync(() -> {
            long pid = -1L;
            try {
                JsonObject payload = gson.fromJson(rawContent, JsonObject.class);
                if (payload != null && payload.has("pid") && !payload.get("pid").isJsonNull()) {
                    pid = payload.get("pid").getAsLong();
                }
            } catch (Exception e) {
                LOG.warn("[NodeProcessHandler] restart_node_daemon bad payload: " + e.getMessage());
            }

            boolean success = false;
            String error = null;
            if (pid > 0) {
                try {
                    success = operations().restartDaemonByPid(pid);
                } catch (Exception e) {
                    error = e.getMessage();
                }
            } else {
                error = "Invalid or missing PID";
            }

            JsonObject result = new JsonObject();
            result.addProperty("pid", pid);
            result.addProperty("success", success);
            result.addProperty("restart", true);
            if (error != null) {
                result.addProperty("error", error);
            }
            pushKillResult(gson.toJson(result));
            scheduleRefresh(RESTART_REFRESH_DELAY_MS);
        });
    }

    private Operations operations() {
        return operations;
    }

    private static Supplier<NodeProcessRegistry> defaultRegistrySupplier(HandlerContext context) {
        if (context.getProject() == null) {
            throw new IllegalStateException("Project is not available");
        }
        return () -> NodeProcessRegistry.getInstance(context.getProject());
    }

    private void scheduleRefresh(long delayMs) {
        scheduler.schedule(this::handleGetNodeProcesses, delayMs);
    }

    private void pushUpdate(String json) {
        jsDispatcher.dispatch("window.updateNodeProcesses", json);
    }

    private void pushKillResult(String json) {
        jsDispatcher.dispatch("window.nodeProcessKillResult", json);
    }

    private String buildProcessListJson(List<NodeProcessInfo> processes) {
        long now = System.currentTimeMillis();
        int daemonCount = 0;
        int channelCount = 0;
        int orphanCount = 0;

        JsonArray array = new JsonArray();
        for (NodeProcessInfo info : processes) {
            JsonObject item = new JsonObject();
            item.addProperty("id", info.getId());
            item.addProperty("kind", info.getKind().name());
            if (info.getProvider() != null) {
                item.addProperty("provider", info.getProvider());
            }
            item.addProperty("pid", info.getPid());
            item.addProperty("alive", info.isAlive());
            item.addProperty("startedAt", info.getStartedAtMs());
            item.addProperty("uptimeMs", info.getUptimeMs());
            if (info.getCommand() != null) {
                item.addProperty("command", info.getCommand());
            }
            if (info.getHeapUsedBytes() >= 0) {
                item.addProperty("heapUsed", info.getHeapUsedBytes());
            }
            item.addProperty("activeRequestCount", info.getActiveRequestCount());
            if (info.getChannelId() != null) {
                item.addProperty("channelId", info.getChannelId());
            }
            if (info.getSessionId() != null) {
                item.addProperty("sessionId", info.getSessionId());
            }
            if (info.getTabName() != null) {
                item.addProperty("tabName", info.getTabName());
            }
            item.addProperty("orphan", info.isOrphan());
            array.add(item);

            switch (info.getKind()) {
                case DAEMON -> daemonCount++;
                case CHANNEL -> channelCount++;
                case ORPHAN -> orphanCount++;
            }
        }

        JsonObject totals = new JsonObject();
        totals.addProperty("daemon", daemonCount);
        totals.addProperty("channel", channelCount);
        totals.addProperty("orphan", orphanCount);
        totals.addProperty("all", processes.size());

        JsonObject root = new JsonObject();
        root.addProperty("snapshotAt", now);
        root.add("totals", totals);
        root.add("processes", array);
        return gson.toJson(root);
    }

    private void runAsync(Runnable work) {
        CompletableFuture.runAsync(work, asyncExecutor)
            .exceptionally(ex -> {
                LOG.warn("[NodeProcessHandler] Async work failed: " + ex.getMessage(), ex);
                return null;
            });
    }

    private static final class RegistryOperations implements Operations {

        private final Supplier<NodeProcessRegistry> registrySupplier;

        private RegistryOperations(Supplier<NodeProcessRegistry> registrySupplier) {
            this.registrySupplier = registrySupplier;
        }

        @Override
        public List<NodeProcessInfo> snapshot() {
            return registrySupplier.get().snapshot();
        }

        @Override
        public boolean killByPid(long pid) {
            return registrySupplier.get().killByPid(pid);
        }

        @Override
        public int killAllOrphans() {
            return registrySupplier.get().killAllOrphans();
        }

        @Override
        public boolean restartDaemonByPid(long pid) {
            return registrySupplier.get().restartDaemonByPid(pid);
        }
    }
}
