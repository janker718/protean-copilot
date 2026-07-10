# 开发进度快照

更新时间：2026-07-11

本文档以当前仓库 `/Users/janker/Documents/ProteanCopilot` 为主，并参考 `/Users/janker/Documents/code/github/jetbrains-cc-gui` 的现状，目的是回答三件事：

1. `ProteanCopilot` 现在到底开发到了哪一层。
2. 和 `jetbrains-cc-gui` 相比，已经补齐了什么、还差什么。
3. 后续应该优先补“运行稳定性”还是继续铺“功能广度”。

---

## 一句话结论

`ProteanCopilot` 已经明显越过“插件原型期”，进入了“多 Provider Agent 插件的第一轮可运行实现期”。

更准确地说：

- IntelliJ 插件壳、JCEF/WebView、消息分发、会话编排、历史入口、权限入口、Claude 主链已经成型。
- Codex 不是“还没开始”，而是已经完成了 bridge、session、history、dependency 的第一轮接入。
- 当前最大差距不再是有没有架构骨架，而是运行时深度、产品化周边能力、以及对真实 IDE 场景的收口程度。

如果用工程语言概括当前阶段：

`ProteanCopilot` 已经搭出与参考项目同方向的主干，但离 `jetbrains-cc-gui` 那种“功能完整、边角丰富、长期演化过”的成熟度还有一段距离。

---

## 当前规模对照

| 维度 | ProteanCopilot | jetbrains-cc-gui |
|---|---:|---:|
| Java 主代码文件数 | 141 | 268 |
| Java 测试文件数 | 24 | 87 |
| WebView `ts/tsx` 文件数 | 402 | 约 500+ |
| WebView 测试文件数 | 84 | 明显更多 |
| Provider | Claude + Codex | Claude + Codex |
| 历史数据源 | Claude + Codex | Claude + Codex |
| Session 编排层 | 已有 | 更成熟 |
| Permission 子系统 | 已落地第一轮 | 更完整 |
| Dependency 管理 | 已落地第一轮 | 更完整 |

这组数字说明两件事：

- 当前项目已经不是“小玩具仓库”，而是有明确分层和测试基础的中等规模插件工程。
- 和参考项目相比，差距主要在“系统厚度”和“产品配套能力”，不是 0 到 1 的缺失。

---

## 与参考项目的整体对照结论

### 已经对齐到同一方向的部分

- 插件主壳：Tool Window、Startup、Status Bar、JCEF 桥接都已具备。
- 前后端通信：Java `handler/core` 分发模型已经形成，WebView 事件回调链也已建立。
- Session 主链：`SessionSendService`、`SessionLifecycleManager`、`SessionMessageOrchestrator`、`SessionProviderRouter` 已经组成独立运行层。
- Provider 抽象：`BaseSDKBridge` + `provider/claude` + `provider/codex` 的结构已经明确，不再是单 Provider 直连。
- 历史体系：`history`、`handler/history`、`provider/*/History*` 都已存在，说明这条线已经进入实现态。
- 权限体系：`permission` 包不只是壳，已经包含 watcher、router、decision store、manager、file protocol 等组件。

### 仍然明显落后的部分

- 参考项目的 Node/ai-bridge 层更厚，运行时辅助脚本和外围服务更多；当前项目仍偏轻。
- 参考项目的产品化配套更全，包括更多 action、watcher、terminal 相关能力、prompt/skill/MCP 管理细节。
- 当前项目虽然接入了 Codex，但很多真实用户路径还缺少系统级手工回归证据。
- 测试数量和覆盖面仍落后于参考项目，尤其是 Java 侧横向能力和复杂交互场景。

---

## 当前已站稳的能力

### 1. 插件壳与 WebView 主壳

这一层已经不是问题核心。

当前仓库已经具备：

- Tool Window 入口与窗口封装
- JCEF 页面装载与双向调用
- WebView React 应用
- 启动期预热与基础状态挂载

和参考项目相比，这里的差距主要不是“能不能跑”，而是附加功能密度和周边体验。

### 2. 消息分发与会话编排骨架

这部分已经进入“主干成型”状态，关键入口包括：

- [HandlerContext.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/core/HandlerContext.java)
- [MessageDispatcher.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/core/MessageDispatcher.java)
- [SessionSendService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/SessionSendService.java)
- [SessionLifecycleManager.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/SessionLifecycleManager.java)
- [SessionMessageOrchestrator.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/SessionMessageOrchestrator.java)
- [SessionProviderRouter.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/SessionProviderRouter.java)

这说明当前项目的会话运行时已经是独立子系统，而不是散落在窗口类里的拼接逻辑。

### 3. Claude 主链

Claude 仍然是当前最稳的一条 provider 链路，相关主干包括：

- [BaseSDKBridge.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/common/BaseSDKBridge.java)
- [ClaudeSDKBridge.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/claude/ClaudeSDKBridge.java)
- [claude-sdk-bridge.mjs](/Users/janker/Documents/ProteanCopilot/src/main/resources/bridge/claude-sdk-bridge.mjs)

这一层已经不是 demo 代码，而是当前插件运行时的稳定基线。

### 4. Codex 第一轮接入已经完成

相对项目早期描述，Codex 现在的状态需要明确上调：它已经不是“待实现”，而是“已接入、待稳定化”。

关键证据：

- [CodexSDKBridge.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/codex/CodexSDKBridge.java) 已处理 `sandboxMode`、`approvalPolicy`、`workingDirectory`、`reasoningEffort` 等 provider 特定参数。
- [codex-sdk-bridge.mjs](/Users/janker/Documents/ProteanCopilot/src/main/resources/bridge/codex-sdk-bridge.mjs) 已承担真实 bridge 角色，而不是占位文件。
- `history` 与 `session` 已经能在 Codex 侧接住 resume / thread 相关语义。
- `provider/codex` 下已经存在 `CodexHistoryReader`、`CodexHistoryParser`、`CodexHistorySource`、`CodexHistoryIndexService`。

所以当前准确表述应该是：`Codex 已经跨过结构接入阶段，正在补运行稳定性与回归证据。`

补充更新（2026-07-11，本轮 P0 收口）：

- [SessionSendService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/SessionSendService.java) 现在在 `session.requiresProviderResume()` 时，对 `Codex` 也会显式走 `resumeSession(...)`，不再只对 `Claude` 生效。
- 历史恢复成功后形成的“下一条消息必须 resume provider thread”语义，现在已经在 Claude / Codex 两条发送链上保持一致。
- Claude 分支原先一条误写成 `resuming Codex` 的运行日志已修正，避免排查时误导。
- 新增 [SessionSendServiceResumeTest.java](/Users/janker/Documents/ProteanCopilot/src/test/java/com/protean/copilot/session/SessionSendServiceResumeTest.java) 回归测试，明确覆盖 Claude / Codex 历史恢复后的发送行为。
- [SessionMessageOrchestratorTest.java](/Users/janker/Documents/ProteanCopilot/src/test/java/com/protean/copilot/session/SessionMessageOrchestratorTest.java) 额外断言：历史恢复失败时不会错误保留 `requiresProviderResume` 状态。
- 参考 `jetbrains-cc-gui` 补齐了节点进程管理主链：新增 [NodeProcessHandler.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/NodeProcessHandler.java)、[NodeProcessRegistry.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/service/NodeProcessRegistry.java)、[NodeProcessInfo.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/service/NodeProcessInfo.java)，不再只是吞掉 `get_node_processes`，而是能回传真实 bridge/orphan 进程快照并支持 kill / restart / cleanup。

### 5. 历史与权限不是“纯桩”

旧文档里把 `HistoryHandler`、`PermissionService` 一概归类为“桩”，这个判断已经不准确。

当前代码显示：

- [HistoryHandler.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/HistoryHandler.java) 已经把加载、删除、导出、标题、收藏、深搜、CLI 会话转换等动作拆到了独立 service。
- [PermissionService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/permission/PermissionService.java) 已经具备 session registry、decision store、dialog router、file protocol、request watcher 等组件。
- [PermissionHandler.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/PermissionHandler.java) 已经实现权限弹窗、AskUserQuestion、PlanApproval 的前后端桥接与超时兜底。

更准确的结论是：

- 历史与权限两条线都已落地第一轮实现。
- 当前问题在于还有多少边界场景、异常流和真实交互回归没有收口，而不是“有没有后端实现”。

### 6. 设置与依赖管理已经迈过最初阶段

- [SettingsService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/settings/SettingsService.java) 已经基于 `PropertiesComponent` 做基础持久化，不再是简单硬编码返回。
- [DependencyManager.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/dependency/DependencyManager.java) 已经具备 Node 环境检测、SDK 安装、卸载、版本检查、状态汇总等能力。
- [DependencyHandler.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/DependencyHandler.java) 说明依赖管理已经进入 Java ↔ WebView 闭环。

这里的现实状态应当理解为：

- 基础依赖管理闭环已经有了。
- 但距离参考项目那种更完整的升级策略、权限辅助、异常提示细节，仍有差距。

---

## 仍然存在的关键缺口

### 1. Node/bridge 运行时厚度不足

和 `jetbrains-cc-gui` 相比，当前项目最明显的差距仍在 Node 侧运行时生态。

参考项目有更丰富的 `ai-bridge` 文件和外围运行时辅助模块；当前仓库主要还是依赖嵌入式 bridge resource。这会带来两个现实影响：

- 运行时问题定位成本更高。
- Provider 扩展、调试、兼容处理的空间还不够大。

### 2. 产品化横向能力不足

参考项目已经有更完整的：

- action 矩阵
- terminal 相关能力
- watcher / monitor 服务
- prompt / skill / MCP 管理周边
- 更丰富的 provider 管理与 usage 视图

当前项目虽然主链在补齐，但横向功能仍偏薄，导致“能跑”和“好用”之间还有距离。

### 3. 真实 IDE 场景下的系统回归证据仍不够

现在最需要补的不是再加一层抽象，而是把现有链路在真实场景里压实，尤其是：

- query / resume / interrupt
- approval / deny / timeout
- sandbox 失败与恢复
- 历史恢复与 provider 切换
- dependency 缺失、安装失败、版本不匹配

当前自动化测试已经提供了不错的基础，但还不能替代真实 provider、真实凭据、真实 IDE 状态下的手工联调。

### 4. Java 与前端测试面仍有提升空间

当前项目已有 24 个 Java 测试文件、84 个 WebView 测试文件，说明测试并非空白。

但和参考项目相比，差距仍然存在于：

- 更多横向服务类测试
- 更复杂交互链路测试
- Provider 异常场景回归
- 历史、权限、diff、settings 的跨层验证

---

## 当前阶段判断

如果把开发阶段拆成四层：

1. 插件原型
2. 单 Provider 可运行
3. 多 Provider 第一轮可运行
4. 产品化稳定与体验打磨

那么 `ProteanCopilot` 现在大致处在：

`第 3 层后半段，正在向第 4 层过渡。`

也就是说：

- 已经不需要再证明“这个架构方向能不能成立”。
- 现在更需要证明“这些子系统在真实 IDE 里能不能稳定协同”。

---

## 建议中的下一阶段优先级

### P0：运行稳定性与回归收口

优先完成这些系统级验证与修补：

- Claude / Codex 的 query、resume、interrupt 手工联调
- permission / ask user / plan approval 的端到端回归
- sandbox / approval / dependency 异常提示统一
- 历史恢复后的会话状态一致性检查

理由：当前最大的风险不在“少一个功能入口”，而在“已有链路是否可靠”。

本轮已完成的 P0 子项：

- `Codex` 历史恢复后的下一条发送已从“误走 query 风险”收口为“显式走 resumeSession”。
- `Node Process Management` 已从“无 handler / 空快照兜底”收口为参考项目同结构实现，`get_node_processes`、`kill_node_process`、`kill_all_orphans`、`restart_node_daemon` 现在都有专用后端支撑。
- `sandbox / approval / dependency` 三类异常提示已进一步统一：session/runtime 与 dependency 安装、卸载、状态查询现在共用同一套稳定消息归类逻辑，前端优先消费规范化 `message` 而不是裸 `Exception.getMessage()`。
- 参考 `jetbrains-cc-gui` 继续补齐了 Codex 路径细节：发送前增加 `runtime access` 明确拦截，permission deny 后会话会主动 interrupt 清理，history restore / thread remap 现在也有更清晰的 `epoch + sessionId` 诊断日志。
- 历史恢复失败时不会残留错误的 provider resume 标记。
- 会话进行中若 provider `updateMessages` 快照暂时落后于刚发送的提问，`SessionMessageOrchestrator` 现在会保留本地尾部 pending user message，避免最新提问在同一 session 中被覆盖丢失。
- 上述行为已有 Java 定向回归保护，而不是只靠人工记忆。
- `PermissionHandler` 现在已有更完整的回包清理回归：Ask User Question 与 Plan Approval 在收到前端响应后会正确完成并移除 pending future。
- `Codex runtime access = inactive` 时，不再等 bridge/runtime 更下游模糊失败，而是在发送前直接返回稳定错误文案。
- permission deny 现在不只是前端收到一个 rejected 信号，Java 侧也会同步 interrupt 当前 session、补发 `onStreamEnd`、清理 loading，并触发对应回归测试。
- `updateSessionId` 处理 Codex thread remap 时，现在会旋转 `runtimeSessionEpoch` 并写入结构化日志，后续排查 runtime thread 漂移更容易。
- `DependencyHandler` 现在已有稳定错误口径回归：依赖状态查询失败时会为所有 SDK 推送统一的 `status=error` payload；`node_not_configured` 的安装失败结果也已有测试保护。
- `check_node_environment` 的双回调行为（`window.nodeEnvironmentStatus` 与 `window.updateNodeEnvironment`）已补回归，避免前后端状态各自漂移。
- `DependencySection` 前端现在也补上了 `node_not_configured` 后端 install result 的 warning toast 回归，避免只在 Java 层有稳定 payload、WebView 却没有稳定提示。
- 新增 `SessionMessageOrchestrator` 回归测试，分别覆盖“快照落后时保留最新 user message”和“provider 快照追平后不重复追加 user message”两条关键路径。

本轮仍未完成、仍需继续推进的 P0 子项：

- 真实 IDE 内的 `query / resume / interrupt` 手工联调
- `permission / ask user / plan approval` 的完整端到端联调

### P1：设置与管理能力对齐参考项目

这一层过去最大的短板，不是前端没有入口，而是很多入口仍停留在“能展示、但后端直接报 unavailable”。

本轮已完成的对齐项：

- [FrontendActionCoverageHandler.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/FrontendActionCoverageHandler.java) 已补齐一批真实设置动作，不再把对应消息统一打回 `This action is not available in the current runtime.`。
- Prompt 管理从只读变成可写：
  - `add_prompt`
  - `update_prompt`
  - `delete_prompt`
  现在都会落到 [CodemossSettingsService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/settings/CodemossSettingsService.java) 的真实持久化，并回推 `window.promptOperationResult` + 最新 prompt 列表。
- MCP 管理从临时内存态对齐到设置持久化：
  - `add/update/delete/toggle_{codex_}mcp_server`
  - `get_{codex_}mcp_servers`
  - `get_{codex_}mcp_server_status`
  现在都走 `CodemossSettingsService` 的 MCP 配置读写；[McpServerManager.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/settings/manager/McpServerManager.java) 也新增了从设置重建运行时 server list 的能力，不再只是一份纯内存 list。
- Skill 管理补齐了第一轮 IDE / 文件系统联动：
  - `open_skill` 可以直接在 IDE 里打开 skill 文件
  - `delete_skill` 会删除对应 skill 文件/目录并回推 `window.skillDeleteResult`
  - `toggle_skill` 会在启用目录与 `.codemoss/skills-disabled` 间移动文件，实现基础启停切换并回推 `window.skillToggleResult`
- `get_mcp_server_tools` / `get_codex_mcp_server_tools` 仍未接入真实 SDK tool 枚举，但已经补了稳定结构化返回，不再让前端拿不到 payload。

本轮新增的定向回归：

- [FrontendActionCoverageHandlerTest.java](/Users/janker/Documents/ProteanCopilot/src/test/java/com/protean/copilot/handler/FrontendActionCoverageHandlerTest.java)
  - 新增 prompt add 行为会持久化并回推最新列表的断言
  - 新增 MCP add 行为会持久化并回推最新列表的断言

这意味着当前项目在“settings / prompt / MCP / skill 管理”上，已经从“有 UI 但多数是假动作”推进到“至少核心 CRUD 和联动动作已经可用”的阶段。
- `sandbox / dependency` 在真实 provider 运行时下的异常提示与恢复验证

本轮相关验证：

- `./gradlew test --tests com.protean.copilot.session.SessionSendServiceResumeTest --tests com.protean.copilot.session.SessionSendServiceTest --tests com.protean.copilot.session.SessionMessageOrchestratorTest --tests com.protean.copilot.session.SessionRuntimeMessagesTest` 已通过
- `./gradlew test --tests com.protean.copilot.session.SessionRuntimeMessagesTest --tests com.protean.copilot.handler.DependencyHandlerTest -PskipWebview=true` 已通过
- `./gradlew test --tests com.protean.copilot.session.SessionSendServiceTest --tests com.protean.copilot.session.SessionSendServiceResumeTest --tests com.protean.copilot.session.SessionMessageOrchestratorTest --tests com.protean.copilot.handler.PermissionHandlerTest -PskipWebview=true` 已通过
- `./gradlew test --tests com.protean.copilot.handler.PermissionHandlerTest --tests com.protean.copilot.handler.DependencyHandlerTest --tests com.protean.copilot.session.SessionSendServiceResumeTest --tests com.protean.copilot.session.SessionMessageOrchestratorTest --tests com.protean.copilot.session.SessionRuntimeMessagesTest` 已通过
- `./gradlew test --tests com.protean.copilot.session.SessionMessageOrchestratorTest -PskipWebview=true` 已通过
- `cd webview && npx vitest run src/components/settings/DependencySection/index.test.tsx src/utils/errorMatcher.test.ts src/hooks/useWindowCallbacks.test.ts` 已通过

### P1：Codex 深化而非继续浅铺功能

下一步比起继续横向铺更多 feature，更值得投入的是：

- Codex provider 的失败恢复
- 历史 / session / permission 在 Codex 路径上的细节补齐
- 运行日志、状态文案、调试可见性增强

理由：Codex 已经接进来，现在最有价值的是把它站稳。

### P2：补横向产品化能力

在主链稳定后，再推进这些更像“成熟插件”的部分：

- 更多 action 和编辑器/终端联动
- 更完整的 skill / prompt / MCP 管理能力
- detached window、watcher、辅助服务
- 更丰富的 settings / usage / provider 体验

### P2.6：skill / prompt / agent 管理闭环继续补齐（本轮新增）

参考 `jetbrains-cc-gui` 的 `PromptHandler / AgentHandler / SkillHandler` 之后，本轮优先补的是当前仓库里“前端已经做了 UI，但 Java 侧仍然 unavailable”的管理动作闭环。

本轮已完成：

- [`FrontendActionCoverageHandler.java`](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/FrontendActionCoverageHandler.java) 不再只承担 prompt CRUD / skill toggle 这类最基础动作，而是继续补齐了：
  - `export_prompts`
  - `import_prompts_file`
  - `save_imported_prompts`
  - `export_agents`
  - `import_agents_file`
  - `save_imported_agents`
  - `import_skill`
- prompt / agent 导入现在已经具备参考项目同类能力的主链：
  - 选择 JSON 文件
  - 校验导出格式
  - 生成 import preview
  - 根据 `skip / overwrite / duplicate` 策略落盘
  - 回推 `window.*ImportPreviewResult` / `window.*ImportResult`
  - 刷新最新列表给前端
- skill 导入不再直接打回 `This action is not available in the current runtime.`，而是支持：
  - Claude 路径导入文件或目录型 skill
  - Codex 路径导入带 `SKILL.md` 的目录型 skill
  - 导入后回推 `window.skillImportResult` 并刷新 skill 列表
- prompt / agent 导出也不再是假动作，当前会将选中项导出为与前端导入链兼容的 JSON 结构，默认写入项目下 `doc/exports/`。

这意味着当前仓库在这几组设置能力上，已经从“有 UI、有回调名、但后端大面积 unavailable”推进到“导入预览、冲突处理、保存结果、刷新列表都可用”的阶段。

本轮回归测试：

- [`FrontendActionCoverageHandlerTest.java`](/Users/janker/Documents/ProteanCopilot/src/test/java/com/protean/copilot/handler/FrontendActionCoverageHandlerTest.java)
  - 新增 prompt 导入 duplicate 策略断言
  - 新增 agent 导入 overwrite 策略断言
  - 继续覆盖 supplemental webview actions 都会被后端消费
- 验证命令：
  - `./gradlew test --tests com.protean.copilot.handler.FrontendActionCoverageHandlerTest`

当前边界：

- 本轮为了先把能力补成可用闭环，仍然把这些逻辑收在 `FrontendActionCoverageHandler` 内部辅助方法里；结构上还没有像参考项目那样完全拆成 `PromptHandler / AgentHandler / SkillHandler / ImportExportSupport`。
- 导出目前默认写到 `doc/exports/`，还没有完全对齐参考项目那种更完整的另存为对话框和 import/export support 抽象。
- watcher / detached helper / editor-terminal 更深联动还未在这一轮继续展开，这部分仍属于后续纵深工作。

### P2.5：ai-bridge 及周边基础设施补齐（本轮新增）

参考 `jetbrains-cc-gui` 对照后，当前仓库在 ai-bridge 方向上的主要差距，之前并不只是“少几个脚本”，而是缺少围绕 bridge 运行的基础设施层。

本轮已补齐的第一批结构：

- 新增 [BridgePathLocator.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/bridge/BridgePathLocator.java)
  - 统一 bridge runtime 根目录定位
  - 支持 `protean.bridge.dir` / `PROTEAN_BRIDGE_DIR` 覆盖
- 新增 [BridgeArchiveExtractor.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/bridge/BridgeArchiveExtractor.java)
  - 负责把 classpath bridge 资源物化到 runtime 目录
  - 先对齐“extractor”职责，而不是继续把资源提取逻辑塞在 `BaseSDKBridge` 里
- 新增 [BridgeDirectoryResolver.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/bridge/BridgeDirectoryResolver.java)
  - 为 provider 解析带作用域的 bridge script 运行目录
  - 让 Claude / Codex bridge 运行目录结构对齐参考项目的 resolver 思路
- 新增 [EnvironmentConfigurator.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/bridge/EnvironmentConfigurator.java)
  - 统一补齐 `PATH`、`HOME`、`CODEX_HOME`、`CLAUDE_PERMISSION_DIR`、`CLAUDE_PERMISSION_SAFETY_NET_MS`
  - 让 bridge 启动和 dependency 安装使用同一套环境补齐逻辑
- 新增 [ProcessManager.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/bridge/ProcessManager.java)
  - 统一 bridge 相关子进程注册、查找、终止、清理

本轮同步接入现有主链：

- [BaseSDKBridge.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/common/BaseSDKBridge.java)
  - 脚本提取改为走 `BridgeDirectoryResolver`
  - 进程环境改为走 `EnvironmentConfigurator`
  - bridge 进程注册/终止/清理改为走 `ProcessManager`
  - 同时把这些 helper 改成懒加载，避免纯单元测试或桥接未启动路径提前触发 IntelliJ service 依赖
- [DependencyManager.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/dependency/DependencyManager.java)
  - npm 安装流程也接入 `EnvironmentConfigurator`，不再自己裸跑 `ProcessBuilder`

本轮新增的 ai-bridge 基础设施回归：

- [BridgeDirectoryResolverTest.java](/Users/janker/Documents/ProteanCopilot/src/test/java/com/protean/copilot/bridge/BridgeDirectoryResolverTest.java)
  - 验证 bridge script 会被物化到 provider scoped runtime 目录
- [EnvironmentConfiguratorTest.java](/Users/janker/Documents/ProteanCopilot/src/test/java/com/protean/copilot/bridge/EnvironmentConfiguratorTest.java)
  - 验证 `PATH/HOME/CODEX_HOME/CLAUDE_PERMISSION_DIR` 等关键环境变量会被稳定补齐
- [BaseSDKBridgeTest.java](/Users/janker/Documents/ProteanCopilot/src/test/java/com/protean/copilot/provider/common/BaseSDKBridgeTest.java)
  - 继续作为回归保护，确保这轮 bridge helper 抽离没有打断既有 session/runtime 语义

本轮验证：

- `./gradlew test --tests com.protean.copilot.bridge.BridgeDirectoryResolverTest --tests com.protean.copilot.bridge.EnvironmentConfiguratorTest --tests com.protean.copilot.provider.common.BaseSDKBridgeTest`

边界说明：

- 当前仓库仍未像参考项目那样引入完整打包式 `ai-bridge.zip`、`channel-manager.js`、签名校验和更厚的 provider-side helper 集群。
- 本轮完成的是“ai-bridge 周边基础设施层”的第一步：先把 bridge 运行目录、环境配置、资源物化、进程托管这些共性职责从 provider/bridge 主类里抽出来并接入现有链路。
- 后续如果继续对齐参考项目，这一层的下一批目标会是：
  - 更完整的 bridge 分发物与版本管理
  - Claude/Codex 更细的 process/channel manager
  - watcher / preloader / detached helper service

### P3：继续扩测试面

建议把新增测试重点放在：

- provider 运行时异常
- history 恢复链路
- permission 超时与兜底
- dependency 安装与状态同步
- WebView 与 Java 的协议契约

---

## 最后的判断

参考 `jetbrains-cc-gui` 来看，`ProteanCopilot` 当前最值得肯定的不是“抄到了多少模块名”，而是已经把多 Provider Agent 插件最难的几条主干都搭出来了。

当前真正的开发重点应该从“继续证明架构”切到“压实运行稳定性，再补产品化细节”。

一句话收尾：

`现在的 ProteanCopilot，已经不是缺骨架，而是到了该把骨架长成肌肉的时候。`
