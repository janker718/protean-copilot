package com.protean.copilot.provider.common;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.diagnostic.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SDK 桥接抽象基类 —— 封装 Node.js 子进程通信的通用机制。
 *
 * <p>各 AI 提供商（Claude、Codex 等）通过继承此类并实现三个抽象方法，
 * 即可复用进程管理、JSON-line 协议、会话生命周期和流式事件处理等
 * 所有通用逻辑，无需重复编写。</p>
 *
 * <h3>子类必须实现的抽象方法</h3>
 * <ul>
 *   <li>{@link #getProviderName()} — 提供商名称，用于日志和线程标识</li>
 *   <li>{@link #getDefaultModel()} — 默认模型标识符</li>
 *   <li>{@link #getBridgeScriptResource()} — 桥接脚本在 classpath 中的资源路径</li>
 * </ul>
 *
 * <h3>可覆写的模板方法</h3>
 * <ul>
 *   <li>{@link #handleReady(JsonObject)} — 处理 Node.js 就绪信号（可添加提供商特有校验）</li>
 * </ul>
 *
 * <h3>线程模型</h3>
 * <ul>
 *   <li><b>Reader 线程</b>：后台 daemon 线程，持续读取 Node.js stdout 的 JSON 行</li>
 *   <li><b>Writer</b>：通过 {@code writerLock} 同步，所有对 Node.js stdin 的写入操作线程安全</li>
 *   <li><b>JS 回调</b>：通过 {@link BridgeCallback} 回到 EDT</li>
 * </ul>
 *
 * @see com.protean.copilot.provider.claude.ClaudeSDKBridge
 */
public abstract class BaseSDKBridge {

    // ==================== 抽象方法（子类必须实现） ====================

    /**
     * 返回提供商名称，用于日志输出和线程命名。
     * 例如：{@code "Claude"}、{@code "Codex"}。
     */
    protected abstract String getProviderName();

    /**
     * 返回默认的 AI 模型标识符。
     * 例如：{@code "claude-sonnet-4-6"}。
     */
    protected abstract String getDefaultModel();

    /**
     * 返回桥接脚本在 classpath 资源中的路径。
     * 例如：{@code "bridge/claude-sdk-bridge.mjs"}。
     */
    protected abstract String getBridgeScriptResource();

    // ==================== 日志 & JSON ====================

    /** 日志记录器（子类可通过 {@link #log()} 访问） */
    private final Logger LOG = Logger.getInstance(getClass());

    /** 全局共享的 Gson 实例，用于 JSON 序列化/反序列化 */
    protected static final Gson GSON = new GsonBuilder()
        .serializeNulls()
        .create();

    /** JSON 解析器 */
    protected static final JsonParser JSON_PARSER = new JsonParser();

    // ==================== 进程管理 ====================

    /** Node.js 子进程 */
    private volatile Process nodeProcess;

    /** 子进程 stdout 读取器 */
    private volatile BufferedReader stdoutReader;

    /** 子进程 stdin 写入器 */
    private volatile BufferedWriter stdinWriter;

    /** 桥接是否正在运行 */
    private volatile boolean running = false;

    /** 是否已请求关闭 */
    private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

    /** 用于优雅关闭的 CountDownLatch */
    private volatile CountDownLatch shutdownLatch;

    // ==================== Reader 线程 ====================

    /** 后台线程，读取 Node.js stdout */
    private volatile Thread readerThread;

    // ==================== Writer 同步 ====================

    /** 写入操作的同步锁，确保线程安全 */
    private final Object writerLock = new Object();

    // ==================== 会话管理 ====================

    /** 活跃会话映射：sessionId → SessionState */
    private final Map<String, SessionState> activeSessions = new ConcurrentHashMap<>();

    // ==================== 回调接口 ====================

    /** 前端回调 —— 将事件桥接到 JCEF webview */
    private volatile BridgeCallback callback;

    // ==================== 内部类型 ====================

    /**
     * 单个会话的运行状态。
     * 跟踪会话 ID、流状态和完成 Future。
     */
    protected static class SessionState {
        /** 会话唯一标识 */
        final String sessionId;

        /** 工作目录 */
        final String cwd;

        /** 当前是否正在流式输出 */
        volatile boolean streaming = false;

        /** 查询完成 Future —— 在 stream_end 或 error 时完成 */
        volatile CompletableFuture<Void> responseFuture;

        SessionState(String sessionId, String cwd) {
            this.sessionId = sessionId;
            this.cwd = cwd;
        }
    }

    /**
     * 前端回调接口 —— 将 SDK 事件桥接到 JCEF webview。
     * 由 {@code ProteanChatWindow} 实现，封装对 webview 中 JavaScript 函数的调用。
     */
    @FunctionalInterface
    public interface BridgeCallback {
        /**
         * 在 webview 中调用 JavaScript 函数。
         * 实现类应确保在 EDT 上执行。
         *
         * @param functionName JavaScript 函数名（如 "onStreamStart"）
         * @param args         传递给 JS 函数的参数
         */
        void callJavaScript(String functionName, String... args);
    }

    // ==================== 便利方法 ====================

    /** 获取日志记录器（子类可在覆写方法中使用）。 */
    protected Logger log() {
        return LOG;
    }

    // ==================== 生命周期方法 ====================

    /**
     * 从 classpath 资源中提取桥接脚本到临时文件。
     *
     * @param resourcePath classpath 中的资源路径
     * @return 提取后的脚本文件绝对路径，失败返回 null
     */
    protected String extractBridgeScript(String resourcePath) {
        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
            if (is == null) {
                LOG.error("桥接脚本资源不存在: " + resourcePath);
                return null;
            }

            String scriptContent;
            try (is) {
                scriptContent = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            Path tempDir = Path.of(System.getProperty("java.io.tmpdir"), "protean-copilot");
            Files.createDirectories(tempDir);

            // 用资源路径的文件名作为临时文件名
            String fileName = Path.of(resourcePath).getFileName().toString();
            Path scriptFile = tempDir.resolve(fileName);
            Files.writeString(scriptFile, scriptContent);

            String path = scriptFile.toAbsolutePath().toString();
            LOG.info("桥接脚本已提取到: " + path);
            return path;

        } catch (Exception e) {
            LOG.error("提取桥接脚本失败: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 启动 Node.js 子进程并开始读取响应。
     * 桥接脚本路径通过 {@link #getBridgeScriptResource()} 自动确定并提取。
     *
     * @param nodePath Node.js 可执行文件路径（"node" 或来自设置的绝对路径）
     * @throws IOException          如果进程启动失败
     * @throws IllegalStateException 如果桥接已在运行
     */
    public synchronized void start(String nodePath) throws IOException {
        if (running) {
            throw new IllegalStateException(getProviderName() + "SDKBridge 已在运行中");
        }

        // 提取桥接脚本
        String resourcePath = getBridgeScriptResource();
        String scriptPath = extractBridgeScript(resourcePath);
        if (scriptPath == null) {
            throw new FileNotFoundException("无法提取桥接脚本: " + resourcePath);
        }

        // 验证桥接脚本是否存在
        Path scriptFile = Path.of(scriptPath);
        if (!Files.exists(scriptFile)) {
            throw new FileNotFoundException("桥接脚本不存在：" + scriptPath);
        }

        LOG.info("正在启动 " + getProviderName() + " SDK 桥接进程...");
        LOG.info("  Node.js 路径: " + nodePath);
        LOG.info("  脚本路径: " + scriptPath);

        // 构建进程：node <scriptPath>
        ProcessBuilder pb = new ProcessBuilder(nodePath, scriptPath);
        pb.environment().put("NODE_ENV", "production");

        // 设置 NODE_PATH 以便 Node.js 能找到 @anthropic-ai/claude-code
        String nodePathEnv = resolveNodePath(scriptPath);
        if (nodePathEnv != null && !nodePathEnv.isEmpty()) {
            pb.environment().put("NODE_PATH", nodePathEnv);
            LOG.info("  NODE_PATH: " + nodePathEnv);
        }

        // 设置工作目录为脚本所在目录（便于 Node.js 解析相对路径模块）
        pb.directory(scriptFile.getParent().toFile());
        pb.redirectErrorStream(false);

        try {
            nodeProcess = pb.start();
        } catch (IOException e) {
            LOG.error("无法启动 Node.js 进程: " + e.getMessage());
            throw new IOException("启动 Node.js 进程失败，请确认 Node.js 已安装且在 PATH 中: " + e.getMessage(), e);
        }

        // 获取输入输出流
        stdoutReader = new BufferedReader(
            new InputStreamReader(nodeProcess.getInputStream(), StandardCharsets.UTF_8));
        stdinWriter = new BufferedWriter(
            new OutputStreamWriter(nodeProcess.getOutputStream(), StandardCharsets.UTF_8));

        // 单独线程读取 stderr 用于日志记录
        String stderrThreadName = getProviderName() + "SDK-stderr";
        Thread stderrThread = new Thread(() -> {
            try (BufferedReader stderrReader = new BufferedReader(
                new InputStreamReader(nodeProcess.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = stderrReader.readLine()) != null) {
                    LOG.info("[node-stderr] " + line);
                }
            } catch (IOException e) {
                if (!shutdownRequested.get()) {
                    LOG.warn("读取 Node.js stderr 时出错: " + e.getMessage());
                }
            }
        }, stderrThreadName);
        stderrThread.setDaemon(true);
        stderrThread.start();

        // 启动 reader 线程
        shutdownLatch = new CountDownLatch(1);
        String readerThreadName = getProviderName() + "SDK-reader";
        readerThread = new Thread(this::readerLoop, readerThreadName);
        readerThread.setDaemon(true);
        readerThread.start();

        running = true;
        shutdownRequested.set(false);

        LOG.info("等待 Node.js 桥接就绪...");
    }

    /**
     * 以异步方式启动桥接。立即返回，当桥接就绪或启动失败时通过 future 通知。
     *
     * @param nodePath Node.js 路径
     * @return 在桥接就绪时完成的 CompletableFuture
     */
    public CompletableFuture<Void> startAsync(String nodePath) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        String threadName = getProviderName() + "SDK-startup";
        Thread startThread = new Thread(() -> {
            try {
                start(nodePath);
                future.complete(null);
            } catch (Exception e) {
                LOG.error("异步启动 " + getProviderName() + "SDKBridge 失败: " + e.getMessage(), e);
                future.completeExceptionally(e);
            }
        }, threadName);
        startThread.setDaemon(true);
        startThread.start();
        return future;
    }

    /**
     * 关闭桥接 —— 发送 shutdown 命令，等待优雅退出，必要时强制终止。
     * 此方法是幂等的：多次调用安全无副作用。
     */
    public synchronized void shutdown() {
        if (!running) {
            return;
        }

        shutdownRequested.set(true);
        LOG.info("正在关闭 " + getProviderName() + " SDK 桥接...");

        // 先完成所有活跃会话的 Future（异常完成）
        for (SessionState state : activeSessions.values()) {
            if (state.responseFuture != null && !state.responseFuture.isDone()) {
                state.responseFuture.completeExceptionally(
                    new CancellationException("桥接已关闭"));
            }
            state.streaming = false;
        }
        activeSessions.clear();

        // 发送 shutdown 命令
        try {
            JsonObject shutdownMsg = new JsonObject();
            shutdownMsg.addProperty("type", "shutdown");
            writeMessage(shutdownMsg);
        } catch (Exception e) {
            LOG.warn("发送 shutdown 命令时出错: " + e.getMessage());
        }

        // 等待进程退出（最多 5 秒）
        try {
            if (nodeProcess != null && nodeProcess.isAlive()) {
                nodeProcess.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 强制终止
        if (nodeProcess != null && nodeProcess.isAlive()) {
            LOG.warn("Node.js 进程未在超时内退出，强制终止");
            nodeProcess.destroyForcibly();
        }

        // 清理资源
        try { if (stdoutReader != null) stdoutReader.close(); } catch (IOException ignored) {}
        try { if (stdinWriter != null) stdinWriter.close(); } catch (IOException ignored) {}

        if (readerThread != null && readerThread.isAlive()) {
            readerThread.interrupt();
        }

        nodeProcess = null;
        stdoutReader = null;
        stdinWriter = null;
        readerThread = null;
        running = false;

        if (shutdownLatch != null) {
            shutdownLatch.countDown();
        }

        LOG.info(getProviderName() + " SDK 桥接已完全关闭");
    }

    /**
     * 检查桥接是否正在运行且健康。
     *
     * @return 如果 Node.js 进程存活且 reader 线程活跃，返回 true
     */
    public boolean isRunning() {
        return running
            && nodeProcess != null
            && nodeProcess.isAlive()
            && readerThread != null
            && readerThread.isAlive();
    }

    // ==================== 会话操作方法 ====================

    /**
     * 发起一个新的查询请求。
     * 如果 sessionId 对应的是新会话，SDK 会创建会话；否则追加消息到已有会话。
     *
     * @param sessionId       会话唯一标识（新会话请使用 UUID）
     * @param prompt          用户输入的提示文本
     * @param cwd             工作目录（项目根路径）
     * @param model           模型标识（为 null 时使用 {@link #getDefaultModel()}）
     * @param permissionMode  权限模式
     * @param reasoningEffort 推理深度（可为 null）
     * @return 一个在查询完成（stream_end）时完成的 CompletableFuture
     * @throws IllegalStateException 如果桥接未运行
     */
    public CompletableFuture<Void> query(
        String sessionId, String prompt, String cwd,
        String model, String permissionMode, String reasoningEffort
    ) {
        if (!isRunning()) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException(
                getProviderName() + " SDK 桥接未运行"));
            return failed;
        }

        SessionState state = activeSessions.computeIfAbsent(sessionId,
            id -> new SessionState(id, cwd));

        if (state.streaming && state.responseFuture != null && !state.responseFuture.isDone()) {
            LOG.info("会话 " + sessionId + " 有查询正在运行，先中断再发起新查询");
            interrupt(sessionId);
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        state.responseFuture = future;
        state.streaming = true;

        String effectiveModel = (model != null && !model.isEmpty()) ? model : getDefaultModel();

        JsonObject msg = new JsonObject();
        msg.addProperty("type", "query");
        msg.addProperty("sessionId", sessionId);
        msg.addProperty("prompt", prompt);
        msg.addProperty("cwd", cwd);
        msg.addProperty("model", effectiveModel);
        msg.addProperty("permissionMode", permissionMode != null ? permissionMode : "bypassPermissions");
        if (reasoningEffort != null && !reasoningEffort.isEmpty()) {
            msg.addProperty("reasoningEffort", reasoningEffort);
        }

        try {
            writeMessage(msg);
            LOG.info("已发送查询: sessionId=" + sessionId
                + ", prompt长度=" + prompt.length()
                + ", model=" + effectiveModel);
        } catch (Exception e) {
            LOG.error("发送查询失败: " + e.getMessage(), e);
            state.streaming = false;
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * 中断指定会话中正在运行的查询。
     */
    public CompletableFuture<Void> interrupt(String sessionId) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        SessionState state = activeSessions.get(sessionId);
        if (state == null || !state.streaming) {
            future.complete(null);
            return future;
        }

        state.streaming = false;

        JsonObject msg = new JsonObject();
        msg.addProperty("type", "interrupt");
        msg.addProperty("sessionId", sessionId);

        try {
            writeMessage(msg);
            LOG.info("已发送中断: sessionId=" + sessionId);
        } catch (Exception e) {
            LOG.warn("发送中断消息失败: " + e.getMessage());
        }

        if (state.responseFuture != null && !state.responseFuture.isDone()) {
            state.responseFuture.complete(null);
        }

        future.complete(null);
        return future;
    }

    /**
     * 恢复一个已存在的会话，发送后续消息。
     */
    public CompletableFuture<Void> resumeSession(String sessionId, String prompt, String cwd) {
        if (!isRunning()) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException(
                getProviderName() + " SDK 桥接未运行"));
            return failed;
        }

        SessionState state = activeSessions.computeIfAbsent(sessionId,
            id -> new SessionState(id, cwd));

        if (state.streaming && state.responseFuture != null && !state.responseFuture.isDone()) {
            interrupt(sessionId);
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        state.responseFuture = future;
        state.streaming = true;

        JsonObject msg = new JsonObject();
        msg.addProperty("type", "resume");
        msg.addProperty("sessionId", sessionId);
        msg.addProperty("prompt", prompt);
        msg.addProperty("cwd", cwd);

        try {
            writeMessage(msg);
            LOG.info("已发送恢复会话: sessionId=" + sessionId);
        } catch (Exception e) {
            LOG.error("发送恢复会话失败: " + e.getMessage(), e);
            state.streaming = false;
            future.completeExceptionally(e);
        }

        return future;
    }

    // ==================== 回调设置 ====================

    /** 设置前端回调接口。 */
    public void setCallback(BridgeCallback callback) {
        this.callback = callback;
    }

    /** 获取当前的前端回调。 */
    public BridgeCallback getCallback() {
        return callback;
    }

    // ==================== 内部：写入器 ====================

    /**
     * 向 Node.js 子进程写入一条 JSON 消息。线程安全。
     */
    private void writeMessage(JsonObject message) throws IOException {
        writeLine(GSON.toJson(message));
    }

    /**
     * 向 Node.js 子进程写入一行原始 JSON 字符串。自动添加换行符并刷新。
     */
    private void writeLine(String jsonLine) throws IOException {
        synchronized (writerLock) {
            if (stdinWriter == null) {
                throw new IOException("stdin writer 未初始化");
            }
            stdinWriter.write(jsonLine);
            stdinWriter.newLine();
            stdinWriter.flush();
        }
    }

    // ==================== 内部：Reader 线程 ====================

    /**
     * Reader 线程主循环 —— 从 Node.js stdout 逐行读取 JSON 并分发处理。
     */
    private void readerLoop() {
        LOG.info("Reader 线程已启动");
        try {
            String line;
            while ((line = stdoutReader.readLine()) != null) {
                processLine(line);
            }
            LOG.info("Node.js stdout 已关闭（EOF）");
        } catch (IOException e) {
            if (!shutdownRequested.get()) {
                LOG.error("Reader 线程异常: " + e.getMessage(), e);
            }
        } finally {
            handleProcessExit();
        }
    }

    /** 处理从 Node.js 读取到的一行 JSON 消息。 */
    private void processLine(String line) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return;

        try {
            JsonObject message = JSON_PARSER.parse(trimmed).getAsJsonObject();
            dispatchMessage(message);
        } catch (Exception e) {
            LOG.warn("无法解析来自 Node.js 的消息: " + e.getMessage()
                + ", 原始内容: " + (trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed));
        }
    }

    /** 根据消息类型分发到对应的处理器。 */
    private void dispatchMessage(JsonObject message) {
        String type = message.has("type") ? message.get("type").getAsString() : "";

        switch (type) {
            case "ready"              -> handleReady(message);
            case "session_created"    -> handleSessionCreated(message);
            case "stream_start"       -> handleStreamStart(message);
            case "content_delta"      -> handleContentDelta(message);
            case "thinking_delta"     -> handleThinkingDelta(message);
            case "block_reset"        -> handleBlockReset(message);
            case "tool_use"           -> handleToolUse(message);
            case "tool_result"        -> handleToolResult(message);
            case "message_update"     -> handleMessageUpdate(message);
            case "status"             -> handleStatus(message);
            case "stream_end"         -> handleStreamEnd(message);
            case "streaming_heartbeat"-> handleStreamingHeartbeat(message);
            case "error"              -> handleError(message);
            case "prewarmed"          -> handlePrewarmed(message);
            default                   -> LOG.info("未知消息类型: " + type);
        }
    }

    // ==================== 事件处理器 ====================

    /**
     * 处理 Node.js 桥接就绪信号。
     * 子类可覆写此方法添加提供商特有的校验逻辑（如检查 SDK 是否安装）。
     */
    protected void handleReady(JsonObject message) {
        String version = message.has("version")
            ? message.get("version").getAsString()
            : "unknown";
        boolean sdkAvailable = message.has("sdkAvailable")
            && message.get("sdkAvailable").getAsBoolean();

        LOG.info("Node.js 桥接已就绪: SDK版本=" + version + ", SDK可用=" + sdkAvailable);

        invokeJsCallback("onBridgeReady", version, String.valueOf(sdkAvailable));
    }

    /** 处理会话创建确认 —— 通知前端更新 sessionId。 */
    private void handleSessionCreated(JsonObject message) {
        String sessionId = message.has("sessionId")
            ? message.get("sessionId").getAsString() : null;
        String cwd = message.has("cwd")
            ? message.get("cwd").getAsString() : null;

        if (sessionId != null) {
            LOG.info("会话已创建: sessionId=" + sessionId + ", cwd=" + cwd);
            invokeJsCallback("updateSessionId", sessionId);
        }
    }

    /** 处理流开始事件。 */
    private void handleStreamStart(JsonObject message) {
        String sessionId = message.has("sessionId")
            ? message.get("sessionId").getAsString() : null;
        LOG.info("流开始: sessionId=" + sessionId);
        invokeJsCallback("onStreamStart");
        invokeJsCallback("showLoading", "true");
    }

    /** 处理内容增量 —— 通知前端追加文本。 */
    private void handleContentDelta(JsonObject message) {
        String delta = message.has("delta") ? message.get("delta").getAsString() : "";
        if (!delta.isEmpty()) {
            invokeJsCallback("onContentDelta", delta);
        }
    }

    /** 处理思维增量 —— 通知前端追加思考内容。 */
    private void handleThinkingDelta(JsonObject message) {
        String delta = message.has("delta") ? message.get("delta").getAsString() : "";
        if (!delta.isEmpty()) {
            invokeJsCallback("onThinkingDelta", delta);
        }
    }

    /** 处理内容块重置 —— 新的 assistant 消息块开始。 */
    private void handleBlockReset(JsonObject message) {
        invokeJsCallback("onBlockReset");
    }

    /** 处理工具调用事件。 */
    private void handleToolUse(JsonObject message) {
        String toolUseId = message.has("toolUseId")
            ? message.get("toolUseId").getAsString() : "";
        String toolName = message.has("toolName")
            ? message.get("toolName").getAsString() : "";
        String toolInput = message.has("toolInput")
            ? message.get("toolInput").toString() : "{}";

        LOG.info("工具调用: " + toolName + " (id=" + toolUseId + ")");
        invokeJsCallback("onToolUse", toolName, toolInput);
    }

    /** 处理工具调用结果事件。 */
    private void handleToolResult(JsonObject message) {
        String toolUseId = message.has("toolUseId")
            ? message.get("toolUseId").getAsString() : "";
        boolean isError = message.has("isError") && message.get("isError").getAsBoolean();

        LOG.info("工具结果: id=" + toolUseId + ", 是否出错=" + isError);
        invokeJsCallback("onToolResult", toolUseId, String.valueOf(isError));
    }

    /** 处理完整的消息更新快照。 */
    private void handleMessageUpdate(JsonObject message) {
        if (message.has("messages")) {
            invokeJsCallback("updateMessages", message.get("messages").toString());
        }
    }

    /** 处理状态更新消息。 */
    private void handleStatus(JsonObject message) {
        String text = message.has("text") ? message.get("text").getAsString() : "";
        LOG.info("状态: " + text);
        invokeJsCallback("updateStatus", text);
    }

    /** 处理流结束事件 —— 完成对应的 Future。 */
    private void handleStreamEnd(JsonObject message) {
        String sessionId = message.has("sessionId")
            ? message.get("sessionId").getAsString() : null;
        boolean interrupted = message.has("interrupted")
            && message.get("interrupted").getAsBoolean();

        LOG.info("流结束: sessionId=" + sessionId + ", 是否中断=" + interrupted);

        if (sessionId != null) {
            SessionState state = activeSessions.get(sessionId);
            if (state != null) {
                state.streaming = false;
                if (state.responseFuture != null && !state.responseFuture.isDone()) {
                    state.responseFuture.complete(null);
                }
            }
        }

        invokeJsCallback("onStreamEnd");
        invokeJsCallback("showLoading", "false");
    }

    /** 处理流式心跳 —— 防止 WebviewWatchdog 误触发。 */
    private void handleStreamingHeartbeat(JsonObject message) {
        invokeJsCallback("onStreamingHeartbeat");
    }

    /** 处理错误事件 —— 通知前端并标记 Future 异常完成。 */
    private void handleError(JsonObject message) {
        String errorMsg = message.has("message")
            ? message.get("message").getAsString() : "未知错误";
        String errorCode = message.has("code")
            ? message.get("code").getAsString() : "UNKNOWN";

        LOG.error("SDK 错误: [" + errorCode + "] " + errorMsg);

        invokeJsCallback("addErrorMessage", errorMsg);
        invokeJsCallback("showLoading", "false");

        for (SessionState state : activeSessions.values()) {
            if (state.streaming) {
                state.streaming = false;
                if (state.responseFuture != null && !state.responseFuture.isDone()) {
                    state.responseFuture.completeExceptionally(
                        new RuntimeException("[" + errorCode + "] " + errorMsg));
                }
            }
        }
    }

    /**
     * 处理 prewarm 完成事件。
     * 完成 __prewarm__ session 的 future 并清理临时状态。
     */
    private void handlePrewarmed(JsonObject message) {
        String status = message.has("status")
            ? message.get("status").getAsString() : "ok";

        SessionState state = activeSessions.get("__prewarm__");
        if (state != null && state.responseFuture != null && !state.responseFuture.isDone()) {
            if ("ok".equals(status)) {
                state.responseFuture.complete(null);
                LOG.info(getProviderName() + " SDK prewarm 完成");
            } else {
                String error = message.has("error")
                    ? message.get("error").getAsString() : "prewarm failed";
                state.responseFuture.completeExceptionally(new RuntimeException(error));
                LOG.warn(getProviderName() + " SDK prewarm 失败: " + error);
            }
        }
        activeSessions.remove("__prewarm__");
    }

    // ==================== 公共：SDK 预加载 ====================

    /**
     * 预加载 SDK，消除首次查询时的启动延迟。
     *
     * <p>通过向 Node.js 进程发送 prewarm 命令，触发 SDK 的 import 和初始化管线。
     * 应在 {@link #start(String)} 完成且收到 ready 信号后调用。</p>
     *
     * <p>如果 prewarm 失败或桥接未运行，方法会以异常完成 future，
     * 但这不影响后续正常查询 —— SDK 将在首次查询时按需加载。</p>
     *
     * @param cwd            工作目录（项目根路径），可为 null
     * @param model          模型标识符（为 null 时使用默认）
     * @param permissionMode 权限模式（为 null 时使用 "bypassPermissions"）
     * @return 在 SDK 预加载完成时完成的 CompletableFuture
     */
    public CompletableFuture<Void> prewarm(String cwd, String model, String permissionMode) {
        if (!isRunning()) {
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(new IllegalStateException(
                getProviderName() + " SDK 桥接未运行"));
            return failed;
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        SessionState state = new SessionState("__prewarm__", cwd);
        state.responseFuture = future;
        activeSessions.put("__prewarm__", state);

        JsonObject msg = new JsonObject();
        msg.addProperty("type", "prewarm");
        msg.addProperty("cwd", cwd != null ? cwd : System.getProperty("user.dir"));
        msg.addProperty("model", model != null ? model : getDefaultModel());
        msg.addProperty("permissionMode",
            permissionMode != null ? permissionMode : "bypassPermissions");

        try {
            writeMessage(msg);
            LOG.info(getProviderName() + " SDK prewarm 命令已发送");
        } catch (Exception e) {
            LOG.warn("发送 prewarm 命令失败: " + e.getMessage());
            activeSessions.remove("__prewarm__");
            future.completeExceptionally(e);
        }

        return future;
    }

    // ==================== 内部：进程退出处理 ====================

    /**
     * 当 Node.js 进程退出时调用。
     * 清理所有活跃会话，标记 pending Future 异常完成。
     */
    private void handleProcessExit() {
        running = false;

        int exitCode = -1;
        if (nodeProcess != null) {
            try {
                exitCode = nodeProcess.exitValue();
            } catch (IllegalThreadStateException e) {
                exitCode = -1;
            }
        }

        LOG.info("Node.js 进程已退出，退出码: " + exitCode);

        if (!shutdownRequested.get()) {
            invokeJsCallback("addErrorMessage",
                getProviderName() + " SDK 桥接进程意外退出（退出码：" + exitCode
                + "）。请检查 Node.js 和 SDK 是否正确安装。");
            invokeJsCallback("showLoading", "false");
        }

        for (SessionState state : activeSessions.values()) {
            state.streaming = false;
            if (state.responseFuture != null && !state.responseFuture.isDone()) {
                if (shutdownRequested.get()) {
                    state.responseFuture.completeExceptionally(
                        new CancellationException("桥接已关闭"));
                } else {
                    state.responseFuture.completeExceptionally(
                        new RuntimeException("SDK 进程意外退出，退出码：" + exitCode));
                }
            }
        }
        activeSessions.clear();

        if (shutdownLatch != null) {
            shutdownLatch.countDown();
        }
    }

    // ==================== 内部：NODE_PATH 解析 ====================

    /**
     * 解析 NODE_PATH 以定位 {@code @anthropic-ai/claude-code} 包。
     * 尝试以下路径（按优先级）：
     * <ol>
     *   <li>全局 npm node_modules（{@code npm root -g}）</li>
     *   <li>项目 webview/node_modules（开发环境）</li>
     *   <li>常用全局安装路径</li>
     * </ol>
     */
    private String resolveNodePath(String scriptPath) {
        StringBuilder nodePaths = new StringBuilder();

        // 1. 通过 npm root -g 获取全局 node_modules
        try {
            Process p = new ProcessBuilder("npm", "root", "-g")
                .redirectErrorStream(true)
                .start();
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            p.waitFor(5, TimeUnit.SECONDS);
            if (!output.isEmpty() && Files.isDirectory(Path.of(output))) {
                if (nodePaths.length() > 0) nodePaths.append(File.pathSeparator);
                nodePaths.append(output);
                LOG.info("全局 npm node_modules: " + output);
            }
        } catch (Exception e) {
            LOG.debug("无法获取全局 npm root: " + e.getMessage());
        }

        // 2. 检查 webview/node_modules（开发环境）
        try {
            // scriptPath 在 /tmp/protean-copilot/xxx.mjs
            // 项目根在 ../../../../ 相对于 src/main/resources/bridge/
            Path scriptFile = Path.of(scriptPath);
            // 尝试从多个级别向上查找 webview/node_modules
            for (int levels = 1; levels <= 6; levels++) {
                Path candidate = scriptFile.getParent();
                for (int i = 0; i < levels; i++) {
                    if (candidate != null) candidate = candidate.getParent();
                }
                if (candidate != null) {
                    Path webviewModules = candidate.resolve("webview/node_modules");
                    if (Files.isDirectory(webviewModules)) {
                        if (nodePaths.length() > 0) nodePaths.append(File.pathSeparator);
                        nodePaths.append(webviewModules.toAbsolutePath().toString());
                        LOG.info("找到 webview/node_modules: " + webviewModules);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            LOG.debug("查找 webview/node_modules 失败: " + e.getMessage());
        }

        // 3. 常见 macOS/Linux 全局路径
        String home = System.getProperty("user.home");
        String[] commonPaths = {
            "/usr/local/lib/node_modules",
            "/usr/lib/node_modules",
            home + "/.nvm/versions/node/*/lib/node_modules",  // nvm
            home + "/.node_modules",                           // 旧版 npm
            home + "/node_modules",
        };
        for (String path : commonPaths) {
            if (path.contains("*")) {
                // 通配符：尝试最近匹配的
                Path parent = Path.of(path.replace("/*", ""));
                try {
                    if (Files.isDirectory(parent)) {
                        var dirs = Files.list(parent).filter(Files::isDirectory).sorted((a, b) -> b.compareTo(a)).toList();
                        for (var dir : dirs) {
                            Path libModules = dir.resolve("lib/node_modules");
                            if (Files.isDirectory(libModules)) {
                                if (!nodePaths.toString().contains(libModules.toString())) {
                                    if (nodePaths.length() > 0) nodePaths.append(File.pathSeparator);
                                    nodePaths.append(libModules.toAbsolutePath().toString());
                                }
                                break;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            } else if (Files.isDirectory(Path.of(path))) {
                if (!nodePaths.toString().contains(path)) {
                    if (nodePaths.length() > 0) nodePaths.append(File.pathSeparator);
                    nodePaths.append(path);
                }
            }
        }

        return nodePaths.toString();
    }

    // ==================== 内部：JS 回调辅助方法 ====================

    /**
     * 安全地调用 JS 回调。
     * 如果回调已设置且桥接未关闭，则执行。
     */
    private void invokeJsCallback(String functionName, String... args) {
        BridgeCallback cb = callback;
        if (cb == null) return;

        try {
            cb.callJavaScript(functionName, args);
        } catch (Exception e) {
            LOG.warn("调用 JS 回调 '" + functionName + "' 失败: " + e.getMessage());
        }
    }
}
