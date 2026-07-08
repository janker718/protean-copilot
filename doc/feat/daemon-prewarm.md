# Daemon SDK 预加载 — SDK Prewarm

> 状态：✅ 已实现 | 日期：2026-07-08

---

## 问题

首次用户查询需要等待 `import('@anthropic-ai/claude-code')` SDK 加载（3-5 秒），加上 Node.js 进程启动和会话创建，总体首响应延迟 5-10 秒。用户在工具窗口打开后立即输入时体验不佳。

## 方案

在 Node.js 桥接进程启动后、用户首次输入前，通过一个轻量级的 `prewarm` 命令提前触发 SDK 的 `import` 和初始化管线。SDK 模块加载到 Node.js 内存后，后续 `query()` 调用可直接使用，消除冷启动延迟。

### 与 reference (jetbrains-cc-gui) 的差异

| | jetbrains-cc-gui | ProteanCopilot |
|---|---|---|
| 架构 | 应用级 daemon.js + runtime pool + 3 阶段 prewarm | 窗口级单进程 + 单次 prewarm |
| 复杂性 | daemon.js (679行), persistent-query-service (709行), runtime-lifecycle (257行), runtime-registry (176行), DaemonBridge.java (800行), ClaudeDaemonCoordinator.java (247行) | 3 个文件共 ~80 行新增代码 |
| prewarm 策略 | 插件启动 → 新会话 → 首轮完成后，共 3 阶段 | 桥接 ready 后一次 prewarm |
| 持久化 | 应用级单例，跨窗口复用 | 窗口级进程，窗口关闭时销毁 |

ProteanCopilot 采用简化方案：`BaseSDKBridge` 已具备单进程多查询复用能力，仅需在启动后添加一次 SDK 预加载即可覆盖核心场景。

---

## 架构

```text
ProteanChatWindow 构造函数
  │
  ├─ executeOnPooledThread:
  │    ├─ claudeBridge.start(nodePath)
  │    │    └─ ProcessBuilder("node", "claude-sdk-bridge.mjs").start()
  │    │         ├─ stdout reader 线程（JSON-line）
  │    │         ├─ stderr reader 线程
  │    │         └─ Node.js: await import('@anthropic-ai/claude-code')
  │    │              └─ respond({type:'ready', sdkAvailable:true})
  │    │
  │    └─ claudeBridge.prewarm(cwd, null, null)   ← 新增
  │         └─ writeMessage({type:'prewarm', cwd, model, permissionMode})
  │              │
  │              ▼
  │         Node.js: handlePrewarm()
  │              ├─ await import('@anthropic-ai/claude-code')  ← SDK 已在模块顶层导入，此处为幂等
  │              ├─ queryFn({prompt:'', ...})   ← 触发内部初始化（认证、hook 注册等）
  │              └─ respond({type:'prewarmed', status:'ok'})
  │                   │
  │                   ▼
  │              Java: dispatchMessage → handlePrewarmed()
  │                   └─ 完成 __prewarm__ SessionState future
  │
  └─ ... 用户首次输入 ...
       │
       └─ bridge.query(sessionId, prompt, ...)
            └─ Node.js: handleQuery()
                 └─ SDK 已在内存中 → 直接 query() → 首响应 < 2s
```

---

## 实现文件

### 1. `BaseSDKBridge.java` — 新增 `prewarm()` 公共方法

```java
public CompletableFuture<Void> prewarm(String cwd, String model, String permissionMode)
```

- 检查 `isRunning()`，桥接未运行时立即失败。
- 在 `activeSessions` 中注册哨兵键 `"__prewarm__"` 的 `SessionState`。
- 构建 `{type: "prewarm", cwd, model, permissionMode}` JSON 消息，通过 `writeMessage()` 发送到 Node.js stdin。
- 返回 `CompletableFuture<Void>`，在收到 `{type: "prewarmed", status: "ok"}` 时完成。

### 2. `BaseSDKBridge.java` — 新增 `handlePrewarmed()` 事件处理器

```java
private void handlePrewarmed(JsonObject message)
```

- 从 `activeSessions` 中查找 `"__prewarm__"` session。
- `status == "ok"` → 正常完成 future。
- `status == "error"` → 以异常完成 future（不影响后续正常查询）。
- 清理 `"__prewarm__"` session 记录。

### 3. `BaseSDKBridge.java` — `dispatchMessage()` 新增 case

```java
case "prewarmed" -> handlePrewarmed(message);
```

### 4. `claude-sdk-bridge.mjs` — 新增 `handlePrewarm()`

```javascript
async function handlePrewarm(msg) {
    // 确保 SDK 已加载（顶层 await 失败的兜底）
    if (!claudeSDK) {
        claudeSDK = await import('@anthropic-ai/claude-code');
    }
    // 获取 query 函数验证 API 可用
    const queryFn = claudeSDK.query || claudeSDK.default?.query;
    // 创建空 prompt query 触发内部初始化管线
    const query = queryFn({ prompt: '', model, permissionMode, cwd, maxTurns: 0 });
    // 立即关闭释放资源，SDK 模块缓存已驻留内存
    if (query?.close) await query.close();
    respond({ type: 'prewarmed', status: 'ok' });
}
```

### 5. `claude-sdk-bridge.mjs` — `handleMessage()` 新增 case

```javascript
case 'prewarm': return handlePrewarm(msg);
```

### 6. `ProteanChatWindow.java` — 启动后调用 prewarm

```java
claudeBridge.prewarm(project.getBasePath(), null, null)
    .thenAccept(v -> LOG.info("SDK prewarm 完成"))
    .exceptionally(ex -> LOG.warn("SDK prewarm 失败（将在首次查询时加载）: " + ex.getMessage()));
```

---

## 设计决策

### 哨兵 sessionId

使用 `"__prewarm__"` 作为特殊 sessionId，与正常用户 session 隔离。`handlePrewarmed()` 处理完成后立即从 `activeSessions` 中移除，不留残留。

### 失败容错

prewarm 失败仅记录 warning 日志，不影响后续正常查询。如果 SDK 未安装：
- prewarm → `respond({type:'prewarmed', status:'error'})` → future 异常完成 → `LOG.warn`
- 首次 query → SDK 按需加载（与 prewarm 前行为一致）

### 空 prompt query

`handlePrewarm()` 创建 `prompt: ''` 的 query 来触发 SDK 内部初始化（如 OAuth 令牌刷新、hook 注册等），但设置 `maxTurns: 0` 防止 SDK 进入对话循环。立即调用 `query.close()` 释放运行时资源，SDK 的模块级缓存（如认证状态、网络连接池）保留在内存中。

### 不实现应用级 daemon

当前窗口级进程方案已覆盖绝大多数场景。应用级 daemon（跨窗口复用、runtime pool 管理等）复杂度高，可在后续需要多窗口共享会话时再引入。

---

## 性能影响

| 阶段 | 改动前 | 改动后 |
|---|---|---|
| Node.js 进程启动 | ~1-2s | ~1-2s（不变） |
| SDK import 加载 | ~3-5s（首次 query 时） | ~3-5s（prewarm 时，用户无感知） |
| 首次 query 延迟 | 5-10s | < 2s |
| 后续 query 延迟 | < 2s | < 2s（不变） |

---

## 相关文件

- [BaseSDKBridge.java](../src/main/java/com/protean/copilot/provider/common/BaseSDKBridge.java) — `prewarm()` 方法 + `handlePrewarmed()` 处理器
- [claude-sdk-bridge.mjs](../src/main/resources/bridge/claude-sdk-bridge.mjs) — `handlePrewarm()` 函数
- [ProteanChatWindow.java](../src/main/java/com/protean/copilot/ui/toolwindow/ProteanChatWindow.java) — 启动后的 prewarm 调用点
