# 开发进度快照

更新时间：2026-07-10（Codex runtime 恢复链路回归后）

本文档基于两个仓库当前代码状态整理：

- 当前项目：`/Users/janker/Documents/ProteanCopilot`
- 参考项目：`/Users/janker/Documents/code/github/jetbrains-cc-gui`

目标：

1. 梳理当前项目与参考项目的结构对应关系。
2. 说明已经对齐、部分对齐、尚未对齐的能力。
3. 给出下一阶段更准确的开发优先级。

---

## 一句话结论

`ProteanCopilot` 已经从单 Claude 聊天原型，推进到了：

- IntelliJ 插件壳稳定
- Claude 主链可用
- history / permission / session 三层已形成通用骨架
- Codex 的 UI、历史读取、session adapter、provider 入口已接入
- Codex bridge 已进入首轮真实运行接入
- Codex dependency backend 已完成首轮锁版与回归收口

但和参考项目相比，当前最明显的现实状态不是“没有多 Provider”，而是：

- `多 Provider 结构已起`
- `Codex 已跨过结构接入，正在做运行稳定化`
- `@openai/codex-sdk` 已完成真实安装、锁版、lockfile 同步与运行环境说明
- `首页 Checking SDK status` 卡住问题已完成首轮修复
- `通用层已抽出，但运行时分层深度还不够`

也就是说，当前项目最大的差距已经从“有没有骨架”，转成了“骨架上的运行时稳定性、权限闭环、provider 深度是否真正站稳”。

---

## 当前客观规模

| 维度 | ProteanCopilot | jetbrains-cc-gui |
|---|---:|---:|
| Java 主代码文件数 | 141 | 268 |
| Java 测试文件数 | 24 | 86 |
| WebView `ts/tsx` 文件数 | 402 | - |
| WebView 测试文件数 | 84 | - |
| Node/bridge JS 文件数 | 2 个 bridge resource | 77 个 ai-bridge JS/CJS/MJS |
| Claude provider | 已接主链 | 完整 |
| Codex provider | 首轮 bridge 已接通，依赖/稳定化补齐中 | 完整 |
| history provider source | Claude + Codex | Claude + Codex |
| session router / orchestrator | 已有 | 完整 |
| permission 闭环 | 第一轮成形 | 更完整 |

本轮进度判断基于当前工作树代码状态与本轮重新执行的验证结果：

- `npm install @openai/codex-sdk@0.143.0 --save-exact` 已在 `webview/` 实际执行完成
- `npm ls @openai/codex-sdk` 已确认锁定为 `0.143.0`
- `./gradlew test --tests com.protean.copilot.provider.common.BaseSDKBridgeTest --tests com.protean.copilot.provider.codex.CodexSDKBridgeTest --tests com.protean.copilot.dependency.DependencyManagerTest compileJava` 已通过
- `node --check src/main/resources/bridge/codex-sdk-bridge.mjs` 已通过
- `npx vitest run src/components/settings/DependencySection/index.test.tsx src/hooks/useWindowCallbacks.test.ts src/hooks/useDialogCountdownTimeout.test.tsx src/components/PermissionDialog.test.tsx src/components/AskUserQuestionDialog.test.tsx src/components/PlanApprovalDialog.test.tsx` 已通过
- `npx tsc -p tsconfig.test.json --noEmit` 已通过
- `./gradlew test --tests com.protean.copilot.session.SessionRuntimeMessagesTest --tests com.protean.copilot.session.SessionMessageOrchestratorTest --tests com.protean.copilot.session.SessionSendServiceTest --tests com.protean.copilot.session.SessionProviderRouterTest --tests com.protean.copilot.session.SessionLoadServiceTest --tests com.protean.copilot.session.HistorySessionLoadRequestTest --tests com.protean.copilot.handler.PermissionHandlerTest --tests com.protean.copilot.dependency.DependencyManagerTest compileJava` 已通过
- `cd webview && npx vitest run src/hooks/useWindowCallbacks.test.ts src/hooks/useSessionManagement.test.ts src/components/PermissionDialog.test.tsx src/components/settings/DependencySection/index.test.tsx src/components/settings/DependencySection/versioning.test.ts src/components/settings/CodexProviderSection/CodexProviderSection.test.tsx src/components/settings/hooks/useCodexProviderManagement.test.ts src/utils/errorMatcher.test.ts` 已通过
- `./gradlew runIde` 已成功返回，可确认当前插件沙箱可启动
- `cd webview && npx vitest run src/hooks/useWindowCallbacks.test.ts src/hooks/providers/useUsageTracking.test.ts` 已通过
- `./gradlew compileJava` 已通过
- `./gradlew test` 已通过；新增的 `StreamMessageCoalescerTest` 覆盖模型消息快照在 Java -> JCEF 边界的 JSON 传输契约
- `./gradlew test` 最近一次全量通过；`BaseSDKBridgeTest`、`CodexSDKBridgeTest`、`SessionMessageOrchestratorTest`、`SessionSendServiceTest`、`SessionRuntimeMessagesTest` 覆盖本轮 Codex runtime 恢复改动
- `cd webview && npm test` 最近一次全量通过：`84` 个测试文件、`689` 个用例，并完成测试 TypeScript 编译检查
- `node --check src/main/resources/bridge/codex-sdk-bridge.mjs` 与 `node --check src/main/resources/bridge/claude-sdk-bridge.mjs` 最近一次均通过
- `./gradlew runIde` 最近一次成功返回；当前插件 sandbox 可按最新产物启动

本轮额外确认：

- Settings 的 SDK 状态回调不再把 JSON 二次转义；`DependencyHandler` 直接向 WebView 传递 JSON 对象。
- `DependencySection` 会消费页面初始化前积压的状态、版本与更新事件，并为状态和版本查询提供 `5s` / `8s` 超时兜底。
- `cd webview && npx vitest run src/components/settings/DependencySection/index.test.tsx` 最近一次已通过（8 个用例），覆盖积压事件与超时兜底。
- 本地 IDE 曾选中指向 `doc/` 的临时 Gradle 配置，导致尝试执行不存在的 `/gradlew`；该工作区配置已改回共享的项目根 `.run/Run IDE with Plugin` 配置。此项是本机运行入口排障，不属于共享源码功能变更。

本轮最新流式链路复核：

- 模型返回消息时出现的 `SyntaxError: Expected property name or '}' in JSON at position 2` 已定位为 Java 侧双重转义，而非模型响应、SDK 配置或 provider 返回格式问题。
- [StreamMessageCoalescer.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/StreamMessageCoalescer.java) 曾先转义完整消息 JSON，随后 [ProteanChatWindow.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/ui/toolwindow/ProteanChatWindow.java) 在 JCEF 执行边界再次转义，导致 WebView 收到以 `{\\` 开头的伪 JSON。
- 现已移除合并器中的预转义，只保留 JCEF 边界的一次字符串转义；[StreamMessageCoalescerTest.java](/Users/janker/Documents/ProteanCopilot/src/test/java/com/protean/copilot/session/StreamMessageCoalescerTest.java) 使用含嵌套对象、引号和换行的消息快照回归验证。
- 该修复已通过全量 Gradle 测试和 Node 侧等价复现验证；仍需在 IDE 中以真实 Claude/Codex 流式响应完成手工确认。

但需要明确区分：

- 上述通过项已覆盖本轮新增的 `dependency/*`、`DependencyHandler`、`NodeDetector`、`BaseSDKBridge` 与 `codex-sdk-bridge.mjs` 改动
- Gradle 验证过程同时重建了 WebView 产物并同步到了 `src/main/resources/html/protean-chat.html`
- 这次不是只看静态代码，而是补做了真实 SDK 安装、Java 定向回归、WebView 定向回归与测试 TS 编译检查
- 本轮还新增了 Codex runtime 恢复链路的 Java/WebView 定向验证与错误口径统一回归

但本次仍未完成的，是更高层的人工回归与更宽的系统测试面：

- IDE 内真实 WebView 手工联调：`query / resume / interrupt / approval / sandbox / timeout / failure` 全场景点击回归
- 带真实 Codex provider 凭据与 thread 恢复数据的端到端手工验证
- 真实 IDE 账号态下的 Java / WebView 联动回归；自动化全量测试已经通过，但不能替代真实 provider 的网络、凭据与工具授权行为

因此本文档中的“已完成”表示：

- 代码已存在
- 接口边界已落地
- 结构上已经进入真实实现阶段
- 或者已经有自动化回归证据支撑

不表示行为已经与参考项目等价，也不表示 Codex 相关链路已经完成系统级稳定性验证。当前文档里凡是明确写“仍未完成”“待手工回归”“待端到端验证”的部分，都应理解为：代码层已进入实现态，但系统级稳定性还在继续收口。

补充说明：

- 本轮更细的 Codex 运行回归证据已单独整理到
  [codex-runtime-regression.md](/Users/janker/Documents/ProteanCopilot/doc/codex-runtime-regression.md)

---

## 结构对照

### 已经形成的主干分层

当前项目已具备这些与参考项目方向一致的核心边界：

- `bridge`
- `cache`
- `handler/core`
- `handler/history`
- `handler/diff`
- `handler/provider`
- `history`
- `permission`
- `provider/common`
- `provider/claude`
- `provider/codex`
- `session`
- `settings`
- `startup`
- `ui/toolwindow`
- `util`

这说明当前项目已经不再是“窗口类包打天下”的结构，主干分层基本已成型。

### 仍偏薄或未对齐的子系统

相对参考项目，当前还明显偏薄：

- 更完整的 provider 运行时服务层
- 更完整的 skill / prompt / MCP 后端闭环
- terminal / watcher / detached UI 等横向能力
- 更完整的 Node ai-bridge 分层
- 更高密度测试面

结论：

- Java 侧结构已经开始接近参考项目。
- Node 侧运行时与产品化服务层仍是当前最大短板。

---

## 已对齐或接近对齐的部分

### 1. 插件壳与 WebView 主壳

当前项目已经具备完整的 IntelliJ 插件基础入口，包括：

- tool window
- startup activity
- status bar
- JCEF 页面初始化
- React/Vite singlefile 接入

这一层与参考项目已经是同一方向，差距主要在附加窗口、更多 action 和更丰富的运行时管理细节。

### 2. handler/core 分发模式

当前项目已形成：

- [HandlerContext.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/core/HandlerContext.java)
- [MessageHandler.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/core/MessageHandler.java)
- [BaseMessageHandler.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/core/BaseMessageHandler.java)
- [MessageDispatcher.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/core/MessageDispatcher.java)

这层已经具备参考项目式的消息分发骨架，后续缺口主要在 handler 覆盖面，而不是分发方式本身。

### 3. Claude 主链

Claude provider 主链已具备：

- [BaseSDKBridge.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/common/BaseSDKBridge.java)
- [ClaudeSDKBridge.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/claude/ClaudeSDKBridge.java)
- [claude-sdk-bridge.mjs](/Users/janker/Documents/ProteanCopilot/src/main/resources/bridge/claude-sdk-bridge.mjs)
- `SessionSendService` / `SessionCallbackAdapter` / `SessionLifecycleManager` 接线

这部分已经脱离 demo 阶段，是当前项目最稳的一条 provider 主链。

### 3.1 Codex bridge 首轮运行接入已落地

本轮对照代码后，可以明确确认这几件事已经不再是占位：

- [BaseSDKBridge.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/common/BaseSDKBridge.java) 已具备 provider-specific `query/resume/prewarm` 扩展点。  
- [CodexSDKBridge.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/codex/CodexSDKBridge.java) 已能透传 `thread`、`sandbox`、`approval`、`workingDirectory`、`modelReasoningEffort` 等 provider 特定参数。  
- [codex-sdk-bridge.mjs](/Users/janker/Documents/ProteanCopilot/src/main/resources/bridge/codex-sdk-bridge.mjs) 已从 stub 补成真实 bridge，具备 `query`、`resume`、`interrupt`、`prewarm`、`shutdown` 主命令以及流式事件回传能力。  
- `requestSessionId -> 实际 session/thread id` 的 remap 语义已经进入 bridge 与 session 协调链路，不再只是窗口层临时约定。
- history replay 成功后，[SessionMessageOrchestrator.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/SessionMessageOrchestrator.java) 会显式标记下一条 Codex 消息为 runtime `resume`；[SessionSendService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/SessionSendService.java) 仅在该次恢复成功后清除标记，失败可继续重试。
- `resume` 现在保留调用方的 `permissionMode`：`plan -> read-only/untrusted`、`acceptEdits -> workspace-write/on-request`，不再固定退回 `default` 组合。

这意味着 Codex 现在更准确的状态已经不是“结构接入中”，而是“运行接入已完成第一轮，正在补稳定化”。

### 3.3 Codex runtime 恢复链路与错误口径已补一轮收口

本轮新增或完成的收口包括：

- [SessionRuntimeMessages.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/SessionRuntimeMessages.java) 统一 provider unavailable、resume failed、permission denied、sandbox denied、bridge process exit 等用户文案
- [ChatWindowDelegate.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/ui/ChatWindowDelegate.java) 去掉 Claude-only 可用性判断，改为按 active provider 走通用 SDK 运行判定
- [SessionLifecycleManager.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/SessionLifecycleManager.java)、[HistoryMessageInjector.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/history/HistoryMessageInjector.java)、[SessionMessageOrchestrator.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/SessionMessageOrchestrator.java) 已统一历史恢复失败口径
- [BaseSDKBridge.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/common/BaseSDKBridge.java) 已统一 `phase/code/hint/sessionId` 日志与恢复状态回传
- bridge error、history resume error 均同时写入错误消息与状态提示，并在 history failure 时显式释放 loading，避免用户只看到 toast 却仍处于加载态
- WebView `errorMatcher` 已能识别统一后的 resume / permission / sandbox 错误
- [ProteanChatWindow.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/ui/toolwindow/ProteanChatWindow.java) 已补齐 `event:content` 冒号协议消息分发，`get_dependency_status:` 不再被窗口层静默吞掉
- [useUsageTracking.ts](/Users/janker/Documents/ProteanCopilot/webview/src/hooks/providers/useUsageTracking.ts) 已增加 SDK 状态初始化超时兜底，避免首页在 dependency/status 回调丢失时永久停留在 `Checking SDK status`

这使当前项目在 “provider 抛错 -> Java 归一 -> WebView 识别 -> 用户可见提示” 这一链路上，已经比上一版快照稳定一层。

补充说明：

- 这次首页问题的直接根因，不是 dependency manager 本身，而是前端发送的 `get_dependency_status:` 没有被 Java 窗口层继续分发到 handler
- 修复后，首页 SDK 状态链路已经从“可能无限等待”提升为“正常回调可落地，异常场景也有前端超时兜底”

### 3.2 Codex dependency backend 已开始补齐

相对上一版快照，当前工作树已经新增：

- [SdkDefinition.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/dependency/SdkDefinition.java)
- [DependencyManager.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/dependency/DependencyManager.java)
- [InstallResult.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/dependency/InstallResult.java)
- [UpdateInfo.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/dependency/UpdateInfo.java)
- [DependencyHandler.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/DependencyHandler.java)

已经落地的方向包括：

- Claude / Codex SDK 的统一 definition
- 基于 Node/npm 的本地安装目录管理
- settings WebView 的 dependency IPC 回调接线
- 版本查询、安装、卸载、更新检查、Node 环境检测入口

本轮已完成的收口：

- `SdkDefinition.java`、`webview/package.json`、`webview/package-lock.json` 已统一锁定 `@openai/codex-sdk@0.143.0`
- 实际验证了此前写死的 `0.33.0` 不存在，已替换为可安装版本并补了 fallback 列表
- [codex-runtime.md](/Users/janker/Documents/ProteanCopilot/doc/codex-runtime.md) 已补充运行环境、锁版约束、常见失败与排查说明
- `DependencyHandler` 已同时兼容 `window.nodeEnvironmentStatus` 与 `window.updateNodeEnvironment`，消除前后端回调名不一致导致的 UI 假失效
- dependency backend 已纳入本轮 Java/WebView 定向回归

### 4. history 双层模型

[HistoryIndexService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/history/HistoryIndexService.java) 现在已经是：

- 运行期 `SessionIndexCache`
- provider 历史读取 `ProviderHistorySource`
- Claude / Codex 双 provider merge
- 收藏 / 自定义标题 / entrypoint 合并输出

并且已经接入：

- [ClaudeHistorySource.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/claude/ClaudeHistorySource.java)
- [CodexHistorySource.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/codex/CodexHistorySource.java)

这说明历史列表主入口已经具备通用化方向，不再只是 Claude-only 的内存列表。

### 5. session 通用层第一轮拆分

当前 session 层已形成：

- [SessionProviderRouter.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/SessionProviderRouter.java)
- [SessionProviderAdapter.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/SessionProviderAdapter.java)
- [SessionMessageOrchestrator.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/SessionMessageOrchestrator.java)
- [MessageMerger.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/MessageMerger.java)
- [ReplayDeduplicator.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/ReplayDeduplicator.java)
- [ClaudeSessionProviderAdapter.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/ClaudeSessionProviderAdapter.java)
- [CodexSessionProviderAdapter.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/CodexSessionProviderAdapter.java)

这一层和参考项目相比，已经不再缺“router / orchestrator / replay merge”这些基础件，当前问题变成：

- 窗口层还没有完全退后
- provider-specific message handler 还没有再细拆

### 6. permission 第一轮闭环

当前权限系统已形成这一组核心类：

- [PermissionService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/permission/PermissionService.java)
- [PermissionManager.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/permission/PermissionManager.java)
- [PermissionDecisionStore.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/permission/PermissionDecisionStore.java)
- [PermissionToolCatalog.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/permission/PermissionToolCatalog.java)
- [PermissionRequestWatcher.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/permission/PermissionRequestWatcher.java)
- [PermissionDialogRouter.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/permission/PermissionDialogRouter.java)
- [ToolInterceptor.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/permission/ToolInterceptor.java)
- [DiffReviewService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/permission/DiffReviewService.java)

相比更早的文档快照，当前项目已经不能再描述成“permission 只有占位”。

---

## 与参考项目相比的关键差异

### 1. Codex 已跨过结构接入，进入首轮运行接入

当前项目已具备：

- [CodexSDKBridge.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/codex/CodexSDKBridge.java)
- [codex-sdk-bridge.mjs](/Users/janker/Documents/ProteanCopilot/src/main/resources/bridge/codex-sdk-bridge.mjs)
- [CodexHistoryReader.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/codex/CodexHistoryReader.java)
- [CodexHistoryParser.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/codex/CodexHistoryParser.java)
- [CodexHistoryIndexService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/codex/CodexHistoryIndexService.java)
- [CodexSessionProviderAdapter.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/CodexSessionProviderAdapter.java)

但目前更准确的现实状态是：

- Node 侧 [codex-sdk-bridge.mjs](/Users/janker/Documents/ProteanCopilot/src/main/resources/bridge/codex-sdk-bridge.mjs) 已经不是 stub，而是能真实 import `@openai/codex-sdk` 并处理 query/resume/interrupt/prewarm/shutdown。  
- Java 侧 `CodexSDKBridge` 也已不再是最薄桥接，已经承载了 provider-specific 参数透传与权限模式映射。  
- 当前仓库仍未形成参考项目那种 `channel-manager + services/codex/* + permission-mapper` 的更厚 Node 运行时层。

对比参考项目：

- 参考项目已经有 `ai-bridge/channels/codex-channel.js`
- 已有 `ai-bridge/services/codex/*`
- 已有 Codex 事件归一化、权限映射、thread run streamed 的完整处理

结论：

- 当前项目不是“没有 Codex”
- 而是“Codex 的 Java/UI/history/runtime 主链已接通第一轮，但运行时分层和稳定性仍未补完”

### 2. Node ai-bridge 架构差距仍然很大

当前项目的 Node 侧虽然已经从双 stub 时代前进了一步，但主体仍然主要是：

- `claude-sdk-bridge.mjs`
- `codex-sdk-bridge.mjs`

参考项目的 Node 侧则已经形成：

- `channel-manager.js`
- `channels/*`
- `services/claude/*`
- `services/codex/*`
- `utils/permission-mapper.js`
- `permission-ipc.js`
- 其他系统级 bridge/service/util

这带来的直接差异是：

- 当前项目 provider 扩展点主要在 Java 侧
- 参考项目 provider 扩展点同时在 Java 与 Node 侧都已稳定
- 当前项目已修正 Java -> JCEF 的流式 JSON 单次转义契约，但该类边界约束尚未沉淀为参考项目那样的独立 Node channel/service 传输层

也因此，当前项目的多 Provider 虽然已经起步，但可扩展性还未达到参考项目成熟度。

### 3. BaseSDKBridge 通用能力已完成第一轮补强，但仍不够承载完整多 Provider

[BaseSDKBridge.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/common/BaseSDKBridge.java) 目前已经承担了共用进程桥接职责，并在本轮补上了关键扩展点，但相对参考项目和后续扩展需要，仍偏薄：

- provider-specific 的 query/resume/prewarm 构造虽然已存在，但失败恢复、生命周期约束、事件约束仍偏轻量
- session id / thread id 重映射语义已经补了一轮，但还需要更多真实场景回归验证边界
- 更完整的 thread/session 恢复语义仍值得继续沉淀成通用钩子

这意味着：

- Claude 主链足够用
- 但 Codex 这种 thread 模型 provider 接进来时，会继续倒逼 Base bridge 扩展

### 4. history 现在是双 provider，但深度仍不对称

当前项目已经有 Claude + Codex 两条 history source。

但和参考项目相比仍有几个差异：

- Claude 侧更完整，Codex 侧仍偏轻
- 当前项目没有参考项目那种更完整的 `CodexHistorySessionService` / `CodexSessionLiteReader` 组合
- usage 聚合和搜索深度在 Codex 侧仍偏薄
- 历史回放后的 provider-specific message 处理还没有完全沉到独立 handler

结论：

- history 的“provider 化”已经完成第一阶段
- history 的“provider 深度对齐”还没有完成

### 5. session 通用层已抽出，但窗口层粘连仍在

当前项目已经把一部分 provider 细节从窗口层抽到了 router / orchestrator / adapter。

但和参考项目相比仍存在：

- `ProteanChatWindow.java` 仍然知道太多会话细节
- `SessionLifecycleManager.java` 还承担了偏多窗口协作职责
- 历史 session 恢复虽然已经走 orchestrator，但窗口层仍未完全退到 UI 壳
- 当前没有参考项目那类更完整的 `CodexMessageHandler` / provider-specific message handler 体系

这部分的真实状态应描述为：

- 主链已经开始解耦
- 但尚未真正做到“窗口不关心 provider”

### 6. permission 在 Java 侧起得更快，但仍未与多 Provider runtime 完整闭合

当前项目已完成：

- 写文件入口盘点
- 命令执行入口盘点
- diff apply 入口回收
- dialog 生命周期测试补齐
- 决策记忆策略补齐

但与参考项目相比，仍存在：

- provider runtime 层的权限映射尚未完全统一
- Codex runtime 已接通第一轮，但 permission 对 Codex 的真实执行闭环仍主要停留在 Java 配置层与 bridge 参数映射层
- permission 与 provider 事件模型的协同深度不如参考项目

因此：

- permission 不能再算占位
- 但也还不能算“多 Provider 级别的完整执行安全边界”

### 7. 测试面差距仍明显

当前项目已经新增并保留了这些关键测试方向：

- session router / dedupe / merge
- history load
- permission decision store
- permission handler
- Claude / Codex history reader
- Base bridge remap / Codex sandbox-approval 映射

但总体测试规模与参考项目仍差距很大：

- 当前 Java 测试 17 个
- 参考项目 Java 测试 86 个

最明显的缺口仍在：

- Codex runtime bridge
- dependency install / update / node environment
- provider-specific message 处理。参考项目已拆出 `ClaudeMessageHandler` / `CodexMessageHandler`，当前仍主要由通用 orchestrator 承担。
- session lifecycle 与窗口解耦回归
- permission 与 provider 执行链联合回归

---

## 当前阶段判断

如果把项目拆成四层：

1. 插件壳
2. 单 Provider 主链
3. 多 Provider 通用层
4. 产品化 runtime / 权限 / 历史 / 技能闭环

当前项目更准确的位置是：

- 第 1 层：已完成
- 第 2 层：Claude 已完成，Codex 已完成首轮运行接入并补完 dependency backend 锁版
- 第 3 层：已明显起步，正在做系统级手工回归与扩面测试
- 第 4 层：仍在早期阶段

和前一版进度文档相比，最大的修正有三点：

1. 不能再说“多 Provider 还没开始”，因为 `provider/codex + history + session adapter` 已经落地。
2. 也不能继续说“Codex runtime bridge 仍是 stub”，因为 bridge、provider-specific 参数透传、session/thread remap 已经落地。
3. 现在更准确的判断应是：`Codex 首轮运行接入已完成，但系统级稳定化和端到端回归仍未完成`。

---

## 下一阶段优先级

### 第一优先级：把 Codex 首轮运行接入从“定向通过”推进到“系统级稳定回归”

需要优先完成：

1. 在 IDE 内做一次完整的 WebView / provider / permission / session 手工恢复回归，覆盖 query、resume、interrupt、approval、sandbox、超时与失败场景。  
2. 继续补 Codex 运行链路上的测试面，把现在的 bridge / callback / dependency 定向覆盖扩到更完整的 provider runtime 恢复链路。  
3. 继续统一运行期日志、错误提示、异常恢复口径，尤其是 provider 抛错、permission 拒绝、resume 失败三类用户可感知场景。  
4. 补真实 Codex thread 恢复与 sandbox/approval 参数组合的端到端验证记录，避免后续接更多 provider 时重复返工。

本轮相对该优先级的新增完成项：

- 首页 `Checking SDK status` 卡死问题已修复，补齐了窗口层 `event:content` 分发缺口
- 增补了 App 级 SDK 状态加载回归测试与超时兜底测试
- `get_dependency_status` 这条 provider/dependency 启动链路已从“结构存在”推进到“有回归保护”
- `@openai/codex-sdk` 的实际安装、精确版本锁定、`package-lock.json` 同步与环境说明已完成；剩余的是 IDE 内用户操作和真实凭据的手工验证，不是依赖后端实现缺失。
- 模型流式消息的 JSON 双重转义已修复并加入 Java 回归测试，消除了 WebView 在模型返回时 `JSON.parse` 失败的一条确定性传输缺陷。

原因：

- 现在 Java、history、UI 与 bridge 主链都已经接上了，短板已经不是“缺入口”，而是“缺稳定性证明”。  
- dependency backend 这一轮已经收口到可安装、可锁版、可说明、可回归；接下来的短板是系统级恢复场景是否真的站稳。  
- 如果不先把真实运行、恢复、权限场景做扎实，后面的 provider 扩展只会继续放大现有边界风险。

### 第二优先级：继续减薄窗口层与 session 层粘连

需要继续推进：

- history session 恢复完全依赖 `SessionMessageOrchestrator`
- `ProteanChatWindow` 继续只保留 UI 协调职责
- `SessionLifecycleManager` 继续缩到生命周期编排，不再承接 provider 细节
- 补 provider-specific message handler 扩展点

原因：

- 当前这一层已经有骨架
- 再不继续减薄，Codex runtime 一接通，窗口层会重新变厚

### 第三优先级：补 Codex 侧 history / usage 深度

在 runtime 打通后，再继续补：

- Codex history session service
- 更完整的 lite reader / replay 支撑
- usage 聚合
- 更细的历史回放测试

原因：

- 当前 Codex history 已经能读，但仍偏轻
- 应避免在 runtime 未通时先做大量深历史重构

### 第四优先级：把 Node runtime 从单 bridge 资源演进为可维护分层

参考项目的 `ai-bridge` 已拆分 channel、Claude/Codex service、SDK loader、permission mapper 等模块；当前项目只有 `claude-sdk-bridge.mjs` 与 `codex-sdk-bridge.mjs` 两个 bridge resource。

需要在不改变现有 Java adapter/router/orchestrator 边界的前提下，逐步抽出：

- provider event normalizer
- provider-specific permission mapper
- SDK loading / environment helper
- bridge process 与错误恢复公共层

原因：

- 目前 bridge 已能运行，但继续把 provider 差异堆在单文件会放大 Codex 扩展和故障定位成本。
- 这个工作应排在运行稳定化之后、产品功能扩展之前，避免为了加功能再次重构运行时。

### 第五优先级：再扩 skill / MCP / prompt 后端闭环

这部分仍应放在后面，原因很直接：

- 当前项目的真实短板仍在 provider runtime 与通用层站稳
- 过早扩 skill / MCP 只会把薄弱的底座继续放大

---

## 当前结论

`ProteanCopilot` 现在最准确的描述是：

- 已经有参考项目式的主干分层
- Claude 主链已站稳
- history / permission / session 的通用层已基本成形
- Codex 已经从“结构层”推进到“首轮运行层”，但还没完成稳定化收口
- dependency/backend 已完成真实安装、锁版、lockfile、环境说明和 Settings 数据链路的首轮闭环，但还没完成 IDE 内人工安装/卸载与真实凭据回归
- 流式消息 JSON 传输已从双重转义的确定性失败修正为单次边界转义，并已有自动化保护；真实 IDE 流式响应仍待手工回归记录

因此当前与参考项目的核心差异，不再是“有没有这些目录”，而是：

1. Node runtime 体系差距仍大。
2. Codex provider 已有真实 bridge，但事件归一化、分层厚度与稳定性验证仍不足。
3. 依赖后端已完成锁版和定向回归，但真实 IDE 操作、真实 thread 与恢复场景仍缺验证记录。
4. session / permission 虽已拆层，但还未完全产品化。
5. 测试覆盖密度仍明显不足，特别是 provider runtime、事件归一化和生命周期恢复。

后续策略应继续坚持：

- 少加新壳
- 优先补运行时稳定闭环
- 先把 `Codex runtime 稳定化 + session 通用层 + permission 协同` 做实
- 再去追更宽的产品面
