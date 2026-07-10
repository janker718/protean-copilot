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

本轮追加执行：

```bash
./gradlew test \
  --tests com.protean.copilot.provider.common.BaseSDKBridgeTest \
  --tests com.protean.copilot.provider.codex.CodexSDKBridgeTest \
  --tests com.protean.copilot.session.SessionMessageOrchestratorTest \
  --tests com.protean.copilot.session.SessionSendServiceTest \
  --tests com.protean.copilot.session.SessionRuntimeMessagesTest
./gradlew test
```

结果：

- `compileJava` 通过
- 上述 Java 定向测试通过
- Gradle 过程同步完成 WebView 构建与 `protean-chat.html` 产物刷新
- 全量 `./gradlew test` 最近一次通过

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

本轮追加执行：

```bash
cd webview && npm test
```

结果：

- 原有 `8` 个定向测试文件、`103` 个用例通过
- 全量 WebView 回归：`84` 个测试文件、`689` 个用例通过，并完成测试 TypeScript 编译检查

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
  - 恢复请求选择的 `permissionMode`
  - `sandboxMode`
  - `approvalPolicy`
  - `workingDirectory`
- `resume` 不再错误携带 `reasoningEffort`

新增恢复链路验证：

- history replay 成功会将 session 标记为 provider runtime resume
- 下一次 Codex 发送经 `SessionSendService` 走 `resumeSession`，成功后才清除标记；失败会保留标记供重试
- `plan` 恢复为 `read-only/untrusted`，`acceptEdits` 恢复为 `workspace-write/on-request`
- Base bridge 的四参数 `resumeSession` 已有回归，确保 permission mode 穿透到 provider message builder

这证明当前通用 bridge 扩展点能够稳定承接 Codex provider 特定参数，且 history 回放后的下一轮对话不会静默新建 thread。

### Session 恢复失败口径

由 `SessionMessageOrchestratorTest` 与 `SessionRuntimeMessagesTest` 验证：

- `thread/resume returned 404`
- `permission denied`
- `approval denied`
- `sandbox denied`

这些错误现在会被归一到统一用户文案，而不是在窗口层、history 注入层、bridge 层各自拼接不同字符串。

`BaseSDKBridge` 的 provider error 会同时推送 `addErrorMessage` 与相同文本的 `updateStatus`；`SessionLifecycleManager` 的 history resume failure 还会显式发送 `showLoading(false)`。这保证 provider 抛错、permission 拒绝与 resume 失败都有一致的可见错误、状态栏文本和 loading 终态。

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

### 待执行的真实账号态手工矩阵

当前环境下，`runIde` 成功只能证明：

- 插件沙箱可启动
- 当前打包产物没有在 IDE 启动阶段直接炸掉

请在 IDE sandbox 中按下表逐项执行，并把实际 thread id、日志文件和截图或录屏地址填入本表。当前没有真实 Codex 凭据与交互证据，以下项目状态均为“待人工执行”。

| 场景 | 操作与预期 | 必留证据 | 状态 |
|---|---|---|---|
| query | 新建 Codex 会话，发送普通问题；生成 response 与 thread id | IDE log、thread id、截图 | 待人工执行 |
| resume | 从 history 打开该 thread，继续发送；应沿用同一 thread | history id、`resume` 日志、截图 | 待人工执行 |
| interrupt | 流式输出期间点击停止；loading 结束且可再次发送 | interrupt 日志、截图 | 待人工执行 |
| approval | `acceptEdits` 触发写操作；允许与拒绝各一次 | dialog 截图、permission decision 日志 | 待人工执行 |
| sandbox | `plan` 触发写操作应拒绝；`acceptEdits` 应走 workspace-write | sandbox/approval 参数日志、结果截图 | 待人工执行 |
| timeout | 断网或 mock 延迟超过 dialog/SDK timeout；UI 应解除 loading 并可重试 | timeout 日志、截图 | 待人工执行 |
| failure | 使用无效 thread 或模拟 provider error；显示归一化错误并可重试 | error code/phase、状态栏截图 | 待人工执行 |

因此，这一项当前状态只能记为：

- `IDE 可启动：已验证`
- `IDE 内完整手工恢复回归：仍待人工逐项执行并留证`

---

## 真实 Codex Thread / Sandbox / Approval 验证边界

### 已验证

- bridge `query/resume` 参数构造正确
- sessionId / threadId remap 逻辑已进入 Java bridge 与 orchestrator
- resume 失败时的 Java -> WebView 用户可感知链路已统一
- history replay -> runtime resume -> permission/sandbox 参数构造已被自动化测试串联验证

### 仍待补齐

- 带真实 Codex 账号态的 `thread` 恢复
- 不同 `approvalPolicy` 组合下的真实 provider 行为观察
- 不同 `sandboxMode` 组合下的真实 provider 行为观察
- provider 实际抛错后的完整 IDE 用户交互回归记录

结论：

当前已经把“参数会不会传”“失败文案会不会乱”“恢复链路会不会断”这三件事自动化收口；但“真实线上 provider 行为是否与预期完全一致”仍需后续在可交互 IDE 环境中继续补端到端记录。
