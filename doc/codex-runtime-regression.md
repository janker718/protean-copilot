# Codex Runtime Regression Record

更新时间：2026-07-10

## 本轮目标

围绕 Codex 运行链路补齐四类验证记录：

1. WebView / provider / permission / session 恢复链路的自动化回归。
2. provider runtime 恢复链路的 Java 定向测试，而不只停留在 bridge 定向测试。
3. provider 抛错、permission 拒绝、resume 失败三类用户可感知口径的一致性验证。
4. Codex thread 恢复与 sandbox / approval 参数组合的实际传递证据。

---

## 已执行自动化验证

### Java 定向回归

已执行：

```bash
./gradlew test \
  --tests com.protean.copilot.provider.common.BaseSDKBridgeTest \
  --tests com.protean.copilot.provider.codex.CodexSDKBridgeTest \
  --tests com.protean.copilot.session.SessionRuntimeMessagesTest \
  --tests com.protean.copilot.session.SessionMessageOrchestratorTest \
  --tests com.protean.copilot.session.SessionSendServiceTest \
  --tests com.protean.copilot.session.SessionProviderRouterTest \
  --tests com.protean.copilot.session.SessionLoadServiceTest \
  --tests com.protean.copilot.session.HistorySessionLoadRequestTest \
  --tests com.protean.copilot.handler.PermissionHandlerTest \
  --tests com.protean.copilot.dependency.DependencyManagerTest \
  compileJava
```

结果：

- `compileJava` 通过
- 上述 Java 定向测试通过
- Gradle 过程同步完成 WebView 构建与 `protean-chat.html` 产物刷新

### WebView 定向回归

已执行：

```bash
cd webview && npx vitest run \
  src/hooks/useWindowCallbacks.test.ts \
  src/hooks/useSessionManagement.test.ts \
  src/components/PermissionDialog.test.tsx \
  src/components/settings/DependencySection/index.test.tsx \
  src/components/settings/DependencySection/versioning.test.ts \
  src/components/settings/CodexProviderSection/CodexProviderSection.test.tsx \
  src/components/settings/hooks/useCodexProviderManagement.test.ts \
  src/utils/errorMatcher.test.ts
```

结果：

- `8` 个测试文件通过
- `103` 个测试通过

---

## 已覆盖场景

### Query / Resume 参数恢复

由 `CodexSDKBridgeTest` 验证：

- `query` 会携带：
  - `permissionMode`
  - `sandboxMode`
  - `approvalPolicy`
  - `reasoningEffort`
  - `workingDirectory`
- `resume` 会携带：
  - `sandboxMode`
  - `approvalPolicy`
  - `workingDirectory`
- `resume` 不再错误携带 `reasoningEffort`

这证明当前通用 bridge 扩展点已经能稳定承接 Codex provider 特定参数。

### Session 恢复失败口径

由 `SessionMessageOrchestratorTest` 与 `SessionRuntimeMessagesTest` 验证：

- `thread/resume returned 404`
- `permission denied`
- `approval denied`
- `sandbox denied`

这些错误现在会被归一到统一用户文案，而不是在窗口层、history 注入层、bridge 层各自拼接不同字符串。

### 前端错误识别与恢复提示

由 `errorMatcher.test.ts` 验证：

- `Codex session resume failed. thread/resume returned 404.`
- `Codex permission request was denied. approval denied for apply_patch.`
- `Codex sandbox denied the requested operation. Sandbox denied write access.`

三类口径已经能在 WebView 侧被稳定匹配。

### Session / Window 恢复守卫

由 `useWindowCallbacks.test.ts` 与 `useSessionManagement.test.ts` 验证：

- history load 完成会释放 `__sessionTransitioning`
- history load 失败后允许后续消息继续进入
- stale `updateMessages` 不会在 session 切换时污染当前窗口
- interrupted / replay / denied-tool-use 的前端恢复守卫仍然可用

### Permission / Dependency / Provider 设置链路

由以下测试覆盖：

- `PermissionHandlerTest`
- `DependencyManagerTest`
- `CodexProviderSection/CodexProviderManagement` 相关测试
- `PermissionDialog.test.tsx`

这层验证的是：

- dialog 与 timeout 配置仍能正确更新
- Codex dependency / provider 设置回调未被这轮运行时收口破坏

---

## IDE 回归现状

### 已完成

已执行：

```bash
./gradlew runIde
```

结果：

- `runIde` 任务成功返回
- 当前构建产物可启动 IntelliJ sandbox
- 本轮未看到新的启动级 Java/Gradle 报错

### 尚未形成“手工点击完成”证据

当前环境下，`runIde` 成功只能证明：

- 插件沙箱可启动
- 当前打包产物没有在 IDE 启动阶段直接炸掉

但它还不能单独证明以下事项已经人工点选验证完成：

- `query`
- `resume`
- `interrupt`
- `approval`
- `sandbox`
- `timeout`
- `failure`

原因：

- 本轮环境里没有留出可复核的 IDE 交互截图、录屏或逐步操作日志
- `runIde` 返回成功后，也没有产生新的交互级 sandbox log 证据来证明这些场景被逐项点击过

因此，这一项当前状态只能记为：

- `IDE 可启动：已验证`
- `IDE 内完整手工恢复回归：仍待人工逐项执行并留证`

---

## 真实 Codex Thread / Sandbox / Approval 验证边界

### 已验证

- bridge `query/resume` 参数构造正确
- sessionId / threadId remap 逻辑已进入 Java bridge 与 orchestrator
- resume 失败时的 Java -> WebView 用户可感知链路已统一

### 仍待补齐

- 带真实 Codex 账号态的 `thread` 恢复
- 不同 `approvalPolicy` 组合下的真实 provider 行为观察
- 不同 `sandboxMode` 组合下的真实 provider 行为观察
- provider 实际抛错后的完整 IDE 用户交互回归记录

结论：

当前已经把“参数会不会传”“失败文案会不会乱”“恢复链路会不会断”这三件事自动化收口；但“真实线上 provider 行为是否与预期完全一致”仍需后续在可交互 IDE 环境中继续补端到端记录。
