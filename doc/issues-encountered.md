# 开发遇到的问题

更新时间：2026-07-11

## 1. 会话内权限申请没有弹出对话框

### 现象

- 在会话运行过程中触发工具权限申请时，前端没有弹出 permission dialog。
- 用户侧表现为“需要授权，但界面没有任何确认弹窗”，请求看起来像是卡住或被静默拒绝。

### 根因

- 权限请求并不都直接走当前 tab 的 `PermissionHandler`，部分路径会通过 `PermissionGateways.resolve(...)` 调用 `PermissionService.findRegisteredInstance(project)` 先按项目查找一个已注册的 `PermissionService`。
- 之前 `PermissionSessionRegistry.findAnyInstanceForProject(...)` 对同一项目下的多个 session 实例采用“命中任意一个就返回”的策略。
- 当项目里残留旧 session 的 `PermissionService` 时，权限请求可能被路由到旧实例；旧实例关联的 dialog shower 已不再对应当前活跃窗口，于是权限请求发出了，但前端不会在当前会话里弹框。

### 修复

- 将 [`PermissionSessionRegistry.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/permission/PermissionSessionRegistry.java) 的同项目实例查找逻辑改为：优先返回最近活跃的 `PermissionService`，避免旧 session 截走当前窗口的权限弹框。
- 新增 [`PermissionSessionRegistryTest.java`](/Users/janker/Documents/ProteanCopilot/src/test/java/com/protean/copilot/permission/PermissionSessionRegistryTest.java) 回归测试，覆盖：
  - 同一项目下多个 session 时优先选择最近活跃实例
  - active session 优先于 legacy instance

### 二次排查：为什么第一次修复后仍然不弹框

- 第一次修复只覆盖了“权限请求已经产生，但被旧 session 的 `PermissionService`/dialog shower 截走”的分支。
- 后续复查发现，当前工程里还有一条更上游的默认值问题：多个前后端入口把 permission mode 默认设成了 `bypassPermissions`，包括：
  - [`SettingsService.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/settings/SettingsService.java)
  - [`ChatSession.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/ChatSession.java)
  - [`ChatWindowDelegate.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/ui/ChatWindowDelegate.java)
  - [`ClaudeSDKBridge.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/claude/ClaudeSDKBridge.java)
  - [`BaseSDKBridge.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/common/BaseSDKBridge.java)
  - [`useModelProviderState.ts`](/Users/janker/Documents/ProteanCopilot/webview/src/hooks/useModelProviderState.ts)
  - [`useClaudeProvider.ts`](/Users/janker/Documents/ProteanCopilot/webview/src/hooks/providers/useClaudeProvider.ts)
  - [`useModelStatePersistence.ts`](/Users/janker/Documents/ProteanCopilot/webview/src/hooks/providers/useModelStatePersistence.ts)
  - [`TabStateService.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/settings/TabStateService.java)
- 这会导致权限请求根本不触发，表现上仍然是“不会弹框”，但根因其实不是弹框链路失效，而是请求在更早阶段被自动放行了。
- 第二次修复把上述默认值统一收回到 `default`，确保未显式选择自动放行时，真正进入 permission request 流程。

### 验证

- `./gradlew test --tests com.protean.copilot.permission.PermissionSessionRegistryTest --tests com.protean.copilot.handler.PermissionHandlerTest -PskipWebview=true`
- `./gradlew test --tests com.protean.copilot.session.SessionSendServiceTest -PskipWebview=true`
- `cd webview && npx vitest run src/hooks/useMessageSender.context.test.ts`

## 2. `get_node_processes` 前端消息没有后端 handler

### 现象

- 日志出现 `No handler consumed message type: get_node_processes`。
- 前端 `fetchNodeProcesses()` 会发送 `get_node_processes`，但 Java 侧没有任何 handler 消费这条消息。

### 根因

- [`nodeProcessCapabilities.ts`](/Users/janker/Documents/ProteanCopilot/webview/src/utils/nodeProcessCapabilities.ts) 已经定义了 `get_node_processes` -> `window.updateNodeProcesses(json)` 这条协议。
- 但后端 handler 注册中缺少对应实现，导致 `MessageDispatcher` 只能输出未消费警告。

### 为什么第一次修复仍然不够

- 第一次修复只是把 `get_node_processes` 临时塞进 [`FrontendActionCoverageHandler.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/FrontendActionCoverageHandler.java)，返回一个稳定空快照，作用只是消除 `No handler consumed message type: get_node_processes` 警告。
- 这并没有真正实现节点进程管理能力，前端菜单虽然不再报错，但拿不到真实 daemon / orphan 数据，也无法对齐参考项目里的 kill / restart / cleanup 能力。

### 最终修复（对齐 `jetbrains-cc-gui` 结构）

- 参考 `~/Documents/code/github/jetbrains-cc-gui` 的 `NodeProcessHandler`、`NodeProcessRegistry`、`NodeProcessInfo` 结构，在本项目中补齐同职责拆分：
  - 新增 [`NodeProcessHandler.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/NodeProcessHandler.java)
  - 新增 [`NodeProcessRegistry.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/service/NodeProcessRegistry.java)
  - 新增 [`NodeProcessInfo.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/service/NodeProcessInfo.java)
- 在 [`ChatWindowDelegate.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/ui/ChatWindowDelegate.java) 中注册专用 `NodeProcessHandler`，不再让 `FrontendActionCoverageHandler` 承担这类运行时职责。
- 在 [`ProteanChatWindow.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/ui/toolwindow/ProteanChatWindow.java) 中补充窗口级 bridge 访问器，供 registry 聚合 Claude / Codex bridge 进程。
- 在 [`BaseSDKBridge.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/common/BaseSDKBridge.java) 中暴露 inspection 访问口，让 registry 能拿到真实 Node 子进程与 active request 数。
- 当前版本按本项目现状做了适配：由于 Protean 还没有参考项目那套更细的 channel/process manager，registry 先把 Claude / Codex bridge 进程映射为 `DAEMON`，再扫描当前 JVM 派生、但未被窗口持有的 bridge 脚本进程作为 `ORPHAN`。

### 回归测试

- 删掉了此前只验证“空快照兜底”的旧断言，避免文档和测试继续强化错误实现方向。
- 新增 [`NodeProcessHandlerTest.java`](/Users/janker/Documents/ProteanCopilot/src/test/java/com/protean/copilot/handler/NodeProcessHandlerTest.java)，覆盖：
  - `get_node_processes` 会推送真实结构化快照
  - `kill_node_process` 会返回结果并触发刷新

### 验证

- `./gradlew test --tests com.protean.copilot.handler.NodeProcessHandlerTest --tests com.protean.copilot.handler.FrontendActionCoverageHandlerTest -PskipWebview=true`

## 3. sandbox / approval / dependency 异常提示口径不统一

### 现象

- 会话运行时错误已经有 [`SessionRuntimeMessages.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/SessionRuntimeMessages.java) 做统一归类，但 dependency 安装/卸载与状态查询仍有多条路径直接把 `Exception.getMessage()` 原样透传给前端。
- 结果是同类问题在 UI 上会出现不同口径：
  - session 内会显示 `permission request was denied` / `sandbox denied`
  - dependency 区域则可能只显示原始 `approval denied for apply_patch` / `npm install failed` / `status backend exploded`
- 前端虽然已经有稳定的 toast/状态消费逻辑，但后端 payload 不稳定，导致 sandbox / approval / dependency 三类异常无法形成一致的用户心智。

### 根因

- 当前工程只对 session/runtime 错误做了中心化文案归一，而 dependency handler 仍处于“功能能跑，但错误口径分散”的状态。
- [`DependencyHandler.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/DependencyHandler.java) 的多处失败分支分别返回：
  - 纯 `error` code（如 `node_not_configured`）
  - 原始异常字符串
  - 状态面板专用的 error payload
- 前端 [`DependencySection/index.tsx`](/Users/janker/Documents/ProteanCopilot/webview/src/components/settings/DependencySection/index.tsx) 以前也主要消费 `error` 字段，没有优先读取后端规范化后的 user-facing message。

### 修复

- 继续沿用参考项目“集中归一 + 稳定 payload”的方向，把 dependency 类错误也收进统一消息层，而不是再造一套散的文案分支。
- 在 [`SessionRuntimeMessages.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/SessionRuntimeMessages.java) 中新增 dependency 相关入口：
  - `dependencyNodeNotConfigured()`
  - `dependencyStatusUnavailable(...)`
  - `dependencyInstallFailed(...)`
  - `dependencyUninstallFailed(...)`
- 更新 [`DependencyHandler.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/DependencyHandler.java)：
  - `node_not_configured` 除了稳定 code 外，再带稳定 `message`
  - install/uninstall/status 失败时，统一生成面向用户的 `message`
  - 对 `approval denied` / `permission denied` / `sandbox denied` 等细节复用同一套分类逻辑，不再让 dependency 区域与 session 区域各说各话
- 更新 [`dependency.ts`](/Users/janker/Documents/ProteanCopilot/webview/src/types/dependency.ts) 与 [`DependencySection/index.tsx`](/Users/janker/Documents/ProteanCopilot/webview/src/components/settings/DependencySection/index.tsx)，让前端优先展示 `message`，退回到 `error`，从而兼容旧 payload 并优先使用新口径。

### 回归测试

- [`SessionRuntimeMessagesTest.java`](/Users/janker/Documents/ProteanCopilot/src/test/java/com/protean/copilot/session/SessionRuntimeMessagesTest.java) 新增 dependency 口径断言：
  - dependency install 遇到 approval denied 时，能归一为稳定 permission-denied 文案
  - dependency status 查询失败时，能归一为稳定 unavailable 文案
- [`DependencyHandlerTest.java`](/Users/janker/Documents/ProteanCopilot/src/test/java/com/protean/copilot/handler/DependencyHandlerTest.java) 新增/更新断言：
  - status error payload 使用统一 `Dependency status unavailable...` 文案
  - `node_not_configured` 返回稳定 `message`
  - install 失败遇到 approval denied 时返回统一 `message`
- [`DependencySection/index.test.tsx`](/Users/janker/Documents/ProteanCopilot/webview/src/components/settings/DependencySection/index.test.tsx) 更新为优先校验后端 `message` 驱动的 toast，而不是只消费原始 `error`。

### 验证

- `./gradlew test --tests com.protean.copilot.session.SessionRuntimeMessagesTest --tests com.protean.copilot.handler.DependencyHandlerTest -PskipWebview=true`
- `cd webview && npx vitest run src/components/settings/DependencySection/index.test.tsx`

## 4. Codex 路径的失败恢复、历史/permission 收口和调试可见性仍偏弱

### 现象

- 相比 `jetbrains-cc-gui`，当前工程的 Codex 路径虽然已经能跑，但在三类场景上仍然偏弱：
  - `Codex runtime access mode = inactive` 时，没有在发送前给出明确、稳定的失败提示
  - 权限被拒绝后，前端知道 `onPermissionDenied`，但 Java 侧没有保证当前会话/流状态同步 interrupt 清理
  - history restore 与 thread remap 发生时，日志和状态可见性不足，排查 `request session id -> runtime thread id` 的漂移比较费劲

### 对照参考项目后的根因

- 参考项目在 `SessionSendService` 中显式区分 `Codex runtime access` 的可用状态，会在 `inactive` 时快速失败，而不是等更下游的 bridge/runtime 模糊报错。
- 参考项目的窗口层把 permission denied 接成了 `interruptDueToPermissionDenial()`，会同时结束流、清 loading、更新前端状态。
- 参考项目在 Codex 线程 remap、history load、stream 生命周期上打了更清晰的日志，便于复盘恢复链路。
- 当前工程里：
  - [`SessionSendService.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/SessionSendService.java) 之前没有 `getCodexRuntimeAccessError(...)`
  - [`PermissionHandler.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/PermissionHandler.java) 虽然已经有 `PermissionDeniedCallback`，但 [`ChatWindowDelegate.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/ui/ChatWindowDelegate.java) 没有把它真正接到会话 interrupt 逻辑
  - [`SessionMessageOrchestrator.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/SessionMessageOrchestrator.java) 在 history load / `updateSessionId` remap 时的诊断日志还不够明确

### 修复

- 在 [`SessionRuntimeMessages.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/SessionRuntimeMessages.java) 中新增 `codexRuntimeAccessUnavailable()`，给 Codex 本地配置未授权场景一个稳定文案。
- 在 [`SessionSendService.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/SessionSendService.java) 中补齐：
  - `getCodexRuntimeAccessError(...)`
  - 发送前读取 `Codex runtime access mode`
  - 若状态是 `inactive`，则直接失败并返回稳定错误，而不是让下游桥接含糊炸掉
  - 对 Codex `query/resume` 增加 `runtimeAccessMode + runtimeSessionEpoch` 诊断日志
- 在 [`ChatWindowDelegate.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/ui/ChatWindowDelegate.java) 中把 `PermissionHandler.setPermissionDeniedCallback(...)` 真正接到 `interruptDueToPermissionDenial` 风格逻辑上，确保 deny 后：
  - interrupt 当前 session
  - 触发 `onPermissionDenied`
  - 补 `onStreamEnd`
  - 清理 loading
  - 同步标签状态
- 在 [`SessionMessageOrchestrator.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/SessionMessageOrchestrator.java) 中增强：
  - history load 开始/成功/失败时的结构化日志
  - `updateSessionId` 收到 Codex thread remap 后旋转 `runtimeSessionEpoch`
  - 记录 `previousSessionId / requestSessionId / newSessionId / epoch`

### 回归测试

- [`SessionSendServiceTest.java`](/Users/janker/Documents/ProteanCopilot/src/test/java/com/protean/copilot/session/SessionSendServiceTest.java)
  - 新增 `getCodexRuntimeAccessError("inactive")` 的稳定文案断言
- [`SessionSendServiceResumeTest.java`](/Users/janker/Documents/ProteanCopilot/src/test/java/com/protean/copilot/session/SessionSendServiceResumeTest.java)
  - 新增 `Codex runtime access = inactive` 时发送前快速失败的回归
  - 同时适配新的 `SessionSendService` 依赖注入签名
- [`SessionMessageOrchestratorTest.java`](/Users/janker/Documents/ProteanCopilot/src/test/java/com/protean/copilot/session/SessionMessageOrchestratorTest.java)
  - 新增 `updateSessionId` 后会旋转 `runtimeSessionEpoch` 的回归
- [`PermissionHandlerTest.java`](/Users/janker/Documents/ProteanCopilot/src/test/java/com/protean/copilot/handler/PermissionHandlerTest.java)
  - 新增 deny decision 会触发 `PermissionDeniedCallback` 的回归

### 验证

- `./gradlew test --tests com.protean.copilot.session.SessionSendServiceTest --tests com.protean.copilot.session.SessionSendServiceResumeTest --tests com.protean.copilot.session.SessionMessageOrchestratorTest --tests com.protean.copilot.handler.PermissionHandlerTest -PskipWebview=true`

## 5. settings / prompt / MCP / skill 入口大量存在“前端有 UI、后端直接 unavailable”断层

### 现象

- 参考 `jetbrains-cc-gui` 对照时可以看到，当前仓库的 settings 页已经有比较完整的 Prompt / MCP / Skill UI。
- 但 Java 侧 [`FrontendActionCoverageHandler.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/FrontendActionCoverageHandler.java) 之前把大量动作统一打回：
  - `add_prompt` / `update_prompt` / `delete_prompt`
  - `add/update/delete/toggle_{codex_}mcp_server`
  - `open_skill` / `delete_skill` / `toggle_skill`
- 用户侧表现是：界面像是支持这些功能，但一操作就直接报 `This action is not available in the current runtime.`。

### 根因

- 前端 UI 与国际化文本已经先行铺开，但 Java settings handler 仍停留在“协议占位”状态。
- [`CodemossSettingsService.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/settings/CodemossSettingsService.java) 之前只有 prompt 的只读能力和 provider 的完整管理能力，没有把 prompt/MCP 这两组设置真正做成完整 CRUD。
- [`McpServerManager.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/settings/manager/McpServerManager.java) 也只是单纯的内存 list，重启或刷新后无法从统一设置恢复。

### 修复

- 在 [`CodemossSettingsService.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/settings/CodemossSettingsService.java) 中补齐：
  - prompt 的 `add / update / delete`
  - MCP server 的 `list / add / update / delete / enabled toggle`
  - 配置默认段落的 `mcp.claude.servers` / `mcp.codex.servers` 初始化
- 在 [`McpServerManager.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/settings/manager/McpServerManager.java) 中新增 `reloadFromSettings(...)`，让运行时 server list 能从统一设置重建。
- 在 [`FrontendActionCoverageHandler.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/FrontendActionCoverageHandler.java) 中把以下协议改成真实动作：
  - prompt CRUD
  - MCP CRUD / toggle / status refresh
  - `open_skill`（IDE 打开文件）
  - `delete_skill`（删除 skill 文件/目录）
  - `toggle_skill`（在启用目录与 `.codemoss/skills-disabled` 之间移动）
- 补齐回调链路，确保前端能收到：
  - `window.promptOperationResult`

## 6. prompt / agent / skill 管理动作继续堆在单个 coverage handler 里

### 现象

- 当前仓库为了尽快补齐 settings 页可用性，已经把越来越多“原本应该拆分”的逻辑塞进了 [`FrontendActionCoverageHandler.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/FrontendActionCoverageHandler.java)。
- 本轮继续补 prompt / agent 导入导出、skill 导入后，这个类已经同时承担：
  - 普通页面动作兜底
  - prompt / agent 持久化
  - import preview / conflict 策略应用
  - 文件选择与 JSON 校验
  - skill 文件/目录复制

### 为什么先这样做

- 用户当前目标是“参考 `jetbrains-cc-gui` 对齐功能与代码结构，复刻能力”，但仓库里还没有与参考项目等价的 `PromptHandler / AgentHandler / SkillHandler / ProviderImportExportSupport` 拆分基础。
- 这一轮优先级更高的是把前端已经暴露出来的能力补成可用闭环，而不是为了结构好看先做一轮大规模 handler 迁移。
- 因此本轮采用的是“先把能力补实，再把结构债显式记账”的策略。

### 当前取舍

- prompt / agent 导入导出已经可用，但导出路径先固定到项目内 `doc/exports/`，没有完全对齐参考项目那种更完整的另存为对话框与 support 类分层。
- skill 导入已经支持 Claude/Codex 两条路径，但导入逻辑仍是 `FrontendActionCoverageHandler` 内的内联实现，而不是独立 service。

### 后续建议

- 当这批能力继续扩展 watcher / detached helper / provider import-export 时，应该把 [`FrontendActionCoverageHandler.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/FrontendActionCoverageHandler.java) 继续拆细，优先抽出：
  - `PromptHandler`
  - `AgentHandler`
  - `SkillHandler`
  - `ImportExportSupport`
- 这样后续再补 detached window、watcher、更多 editor/terminal 联动时，才不会让 settings 协议入口继续膨胀成新的维护热点。

### 本轮验证

- `./gradlew test --tests com.protean.copilot.handler.FrontendActionCoverageHandlerTest`
  - `window.updateGlobalPrompts` / `window.updateProjectPrompts`
  - `window.updateMcpServers` / `window.updateCodexMcpServers`
  - `window.skillDeleteResult` / `window.skillToggleResult`

### 回归测试

- [`FrontendActionCoverageHandlerTest.java`](/Users/janker/Documents/ProteanCopilot/src/test/java/com/protean/copilot/handler/FrontendActionCoverageHandlerTest.java)
  - 新增 prompt add 持久化与回调断言
  - 新增 MCP add 持久化与回调断言

### 验证

- `./gradlew test --tests com.protean.copilot.handler.FrontendActionCoverageHandlerTest`

## 6. ai-bridge 周边职责长期堆在 BaseSDKBridge / DependencyManager 中，结构与参考项目脱节

### 现象

- 当前仓库虽然已经有 Claude / Codex 的两个 `.mjs` bridge，但 bridge 运行所需的共性职责长期散落在：
  - [`BaseSDKBridge.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/common/BaseSDKBridge.java)
  - [`DependencyManager.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/dependency/DependencyManager.java)
- 具体表现包括：
  - bridge 脚本提取逻辑直接写在 `BaseSDKBridge`
  - 进程环境变量补齐没有独立 helper
  - bridge 子进程注册和清理没有独立 process manager
  - dependency 安装流程和 bridge 启动流程各自拼 `ProcessBuilder` 环境
- 对照 `jetbrains-cc-gui` 时，这一层的代码结构和职责边界明显偏薄，后续想继续补 watcher / helper services / 打包分发物时会越来越难收口。

### 根因

- 当前项目最初只需要让两个 provider bridge 跑起来，所以很多“周边基础设施”直接堆在主链类里也能工作。
- 但随着 Codex、permission、dependency、node process management 都接进来，bridge 已不再只是“执行一段 node 脚本”，而是开始承担稳定运行时的公共底座职责。
- 继续把这些能力塞在 `BaseSDKBridge` 里，会让后续对齐参考项目的 `ai-bridge`、watcher、辅助服务都缺少自然落点。

### 修复

- 参考 `jetbrains-cc-gui` 的 bridge 包职责划分，在当前仓库补齐第一批轻量基础设施类：
  - [BridgePathLocator.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/bridge/BridgePathLocator.java)
  - [BridgeArchiveExtractor.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/bridge/BridgeArchiveExtractor.java)
  - [BridgeDirectoryResolver.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/bridge/BridgeDirectoryResolver.java)
  - [EnvironmentConfigurator.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/bridge/EnvironmentConfigurator.java)
  - [ProcessManager.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/bridge/ProcessManager.java)
- 将 [`BaseSDKBridge.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/common/BaseSDKBridge.java) 的脚本物化、环境配置、bridge 进程注册/清理改为走上述 helper。
- 将 [`DependencyManager.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/dependency/DependencyManager.java) 的 npm 安装流程也统一接入 `EnvironmentConfigurator`。

### 二次排查：为什么第一次抽离后 BaseSDKBridgeTest 全部炸掉

- 第一次改动后，`BaseSDKBridgeTest` 在 `new TestBridge()` 阶段就大量 NPE。
- 根因不是 bridge helper 本身，而是两个初始化时机问题：
  1. `BaseSDKBridge` 里新增的 helper 字段在对象构造阶段就被 eager 初始化，其中 `EnvironmentConfigurator -> CodemossSettingsService -> SettingsService` 会触发 IntelliJ `ApplicationManager.getApplication().getService(...)`，而纯单元测试没有完整 application 容器。
  2. `BaseSDKBridge` 里仍有多处直接访问 `LOG` 字段；字段初始化顺序和早期调用路径叠加后，测试里仍会踩到未就绪状态。

### 最终修复

- 把 `BaseSDKBridge` 中新增的：
  - `BridgeDirectoryResolver`
  - `EnvironmentConfigurator`
  - `ProcessManager`
  全部改为懒加载，只在真正启动 bridge 或执行对应路径时创建。
- 将 `BaseSDKBridge` 中残留的直接 `LOG` 访问统一收口到 `log()` 延迟解析，避免父类早期初始化阶段踩到未就绪依赖。

### 回归测试

- 新增：
  - [`BridgeDirectoryResolverTest.java`](/Users/janker/Documents/ProteanCopilot/src/test/java/com/protean/copilot/bridge/BridgeDirectoryResolverTest.java)
  - [`EnvironmentConfiguratorTest.java`](/Users/janker/Documents/ProteanCopilot/src/test/java/com/protean/copilot/bridge/EnvironmentConfiguratorTest.java)
- 既有：
  - [`BaseSDKBridgeTest.java`](/Users/janker/Documents/ProteanCopilot/src/test/java/com/protean/copilot/provider/common/BaseSDKBridgeTest.java)
  继续作为这轮结构抽离的兼容性回归。

### 验证

- `./gradlew test --tests com.protean.copilot.bridge.BridgeDirectoryResolverTest --tests com.protean.copilot.bridge.EnvironmentConfiguratorTest --tests com.protean.copilot.provider.common.BaseSDKBridgeTest`
