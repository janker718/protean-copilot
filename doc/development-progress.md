# 开发进度快照

更新时间：2026-07-09

本文档基于两个代码库的当前实际状态整理：

- 当前项目：`/Users/janker/Documents/ProteanCopilot`
- 对标项目：`/Users/janker/Documents/code/github/jetbrains-cc-gui`

目的不是泛泛描述“做得怎么样”，而是回答四个具体问题：

1. 当前项目已经实现了哪些核心能力。
2. 相对 `jetbrains-cc-gui`，哪些模块只是骨架，哪些已经形成闭环。
3. 还有哪些关键能力明显缺失。
4. 接下来开发顺序应该怎么排。

---

## 一句话结论

`ProteanCopilot` 已经完成了 **插件主骨架、JCEF + React 前端、Claude SDK 桥接主链、会话与消息分发骨架、基础历史索引和 diff 处理框架**，已经不是空壳。

但如果按 `jetbrains-cc-gui` 的成熟度来对比，当前仍处在：

- **核心主链可运行**
- **平台层已经起步**
- **权限 / Provider 扩展 / 完整历史 / MCP / Skills / Codex / 配置体系仍未补齐**

更准确地说，当前项目已经从“能打开聊天窗”走到了“有清晰架构边界的 Claude-only 原型”，但还没有达到 `jetbrains-cc-gui` 那种“多子系统完整协作”的阶段。

---

## 当前仓库事实

### 代码规模

| 维度 | ProteanCopilot | jetbrains-cc-gui |
|---|---:|---:|
| Java 文件数 | 74 | 268 |
| 前端形态 | React + Vite + singlefile | React + WebView |
| Provider 后端 | Claude 为主 | Claude + Codex |
| 历史系统 | 内存索引 + 局部操作 | Provider 级完整历史体系 |
| 权限系统 | 占位 | 完整权限管理链 |

结论：

- 当前项目的 Java 侧体量大约是参考项目的四分之一。
- 前端 UI 很丰富，但后端能力覆盖面明显小于参考项目。

### 最近已确认的本地状态

已验证：

- `./gradlew compileJava`

说明当前 Java 主干在编译层面是自洽的，包括最近新增的：

- `handler/core/BaseMessageHandler`
- `HistoryHandler` 及其 history service 拆分
- `cache/history/settings.manager` 这批平台化骨架

未在本次进度整理中重新验证：

- `runIde`
- Webview Vitest 测试
- 插件交互级联行为

所以本文档里的“已实现”，默认表示：

- 代码已存在且结构上接通
- 不等于真实产品行为已经和参考项目等价

---

## 当前已实现

### 1. 插件入口和主窗口骨架已就位

当前项目已经具备完整的 IntelliJ 插件基础入口：

- Tool Window
- Action
- Startup Activity
- Status Bar
- JCEF WebView 初始化

关键文件：

- [plugin.xml](/Users/janker/Documents/ProteanCopilot/src/main/resources/META-INF/plugin.xml)
- [ProteanToolWindow.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/ui/ProteanToolWindow.java)
- [ProteanChatWindow.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/ui/toolwindow/ProteanChatWindow.java)
- [ChatWindowDelegate.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/ui/ChatWindowDelegate.java)
- [ProteanStartupActivity.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/startup/ProteanStartupActivity.java)
- [BridgePrewarmActivity.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/startup/BridgePrewarmActivity.java)

对比 `jetbrains-cc-gui`：

- 这层的总体设计方向已经对齐。
- 但 `jetbrains-cc-gui` 还有 detached window、更多 action、更多窗口生命周期管理，这部分当前项目还没有。

### 2. WebView 前端已经很完整

`webview/src` 已经不是 demo，而是完整前端应用，包含：

- 聊天页面
- 历史页面
- 设置页面
- MCP 相关组件
- Provider 相关组件
- Tool block 展示
- Usage / Context / Permission / Rewind / Search 等 UI
- 多语言 i18n
- 大量 hook 和前端测试

结论：

- 当前项目前端成熟度明显高于 Java 后端。
- 很多 UI 已经准备好了，但后端能力还没完全跟上。

### 3. Java ↔ JS 消息分发骨架已对齐参考项目

当前项目已经抽出了：

- [HandlerContext.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/core/HandlerContext.java)
- [MessageHandler.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/core/MessageHandler.java)
- [BaseMessageHandler.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/core/BaseMessageHandler.java)
- [MessageDispatcher.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/core/MessageDispatcher.java)

这说明当前项目已经不再把所有桥接逻辑散落在 `ProteanChatWindow` 里，而是开始进入参考项目那种“handler 可扩展”的结构。

对比 `jetbrains-cc-gui`：

- `handler/core` 这一层已经基本对齐。
- 但 handler 的覆盖面差距很大。参考项目有大量专门 handler，当前项目只覆盖了少数能力。

### 4. Claude SDK 主链已经可以工作

已有核心类：

- [SdkBridge.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/bridge/SdkBridge.java)
- [BaseSDKBridge.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/common/BaseSDKBridge.java)
- [ClaudeSDKBridge.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/claude/ClaudeSDKBridge.java)
- [claude-sdk-bridge.mjs](/Users/janker/Documents/ProteanCopilot/src/main/resources/bridge/claude-sdk-bridge.mjs)

当前主链能力：

- 启动 Node 子进程桥接
- 进行 Claude SDK 通信
- Tool Window 发消息
- 流式回调回到前端
- 中断会话
- prewarm 预热

结论：

- 当前项目已经具备“Claude-only IDE Chat”主链。
- 这是当前最扎实的一层。

### 5. 会话与流式更新链已经初步形成

已有：

- [ChatSession.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/ChatSession.java)
- [SessionSendService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/SessionSendService.java)
- [SessionLifecycleManager.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/SessionLifecycleManager.java)
- [SessionCallbackAdapter.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/SessionCallbackAdapter.java)
- [StreamMessageCoalescer.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/StreamMessageCoalescer.java)

这里和旧文档不同：

- `StreamMessageCoalescer` 已经不是桩，而是有节流、心跳、批量推送逻辑的真实实现。

对比 `jetbrains-cc-gui`：

- 当前项目已有主链，但缺少参考项目那套更细的 session 子模块，例如：
  - `SessionProviderRouter`
  - `SessionMessageOrchestrator`
  - `ClaudeMessageHandler`
  - `CodexMessageHandler`
  - `ReplayDeduplicator`
  - `MessageMerger`
  - `StreamDeltaThrottler`

也就是说：

- 当前项目会话层“能跑”
- 参考项目会话层“已产品化拆分”

### 6. 历史模块已经从“空实现”升级到“部分闭环”

当前项目已有：

- [HistoryHandler.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/HistoryHandler.java)
- [HistoryLoadService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/history/HistoryLoadService.java)
- [HistoryDeleteService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/history/HistoryDeleteService.java)
- [HistoryMessageInjector.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/history/HistoryMessageInjector.java)
- [HistoryExportBridgeService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/history/HistoryExportBridgeService.java)
- [HistoryMetadataBridgeService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/history/HistoryMetadataBridgeService.java)
- [HistorySessionConversionService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/history/HistorySessionConversionService.java)
- [HistoryMetadataService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/history/HistoryMetadataService.java)
- [HistoryIndexService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/history/HistoryIndexService.java)
- [SessionIndexCache.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/cache/SessionIndexCache.java)
- [SessionIndexEntry.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/cache/SessionIndexEntry.java)

当前已经接通的动作：

- `load_history_data`
- `load_session`
- `delete_session`
- `delete_sessions`
- `export_session`
- `toggle_favorite`
- `update_title`
- `delete_title`
- `deep_search_history`
- `convert_to_cli_session`

但要注意真实边界：

- 当前历史系统本质上还是 **项目内存索引驱动**
- 不是像参考项目那样的 **Provider 级磁盘历史读取 + 搜索 + parser + session lite reader**

对比 `jetbrains-cc-gui`，当前缺少：

- `ClaudeHistoryReader`
- `ClaudeHistoryParser`
- `ClaudeHistorySearchService`
- `ClaudeHistoryIndexService`
- `CodexHistoryReader`
- `CodexHistoryParser`
- `CodexHistoryIndexService`
- `SubagentHistoryService`
- Provider 级历史恢复

结论：

- 历史模块已经不是空白。
- 但还只是“本地会话索引层”，还没达到“真实历史系统”。

### 7. Diff 模块已经有完整包边界

当前项目已有：

- [DiffHandler.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/diff/DiffHandler.java)
- [SimpleDiffDisplayHandler.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/diff/SimpleDiffDisplayHandler.java)
- [InteractiveDiffHandler.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/diff/InteractiveDiffHandler.java)
- [InteractiveDiffManager.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/diff/InteractiveDiffManager.java)
- [DiffBrowserBridge.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/diff/DiffBrowserBridge.java)
- [DiffFileOperations.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/diff/DiffFileOperations.java)

结论：

- 和历史模块类似，当前 diff 不再是想法，已经有明确代码组织。
- 但相对参考项目的权限审批链，它还没有接入完整的“工具权限 -> diff 审查 -> 用户决策 -> 写入”链路。

---

## 当前未实现或明显不完整

### 1. 权限系统基本还是占位

当前文件：

- [PermissionService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/permission/PermissionService.java)
- [PermissionHandler.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/PermissionHandler.java)

实际情况：

- `PermissionHandler` 只有 `clearPendingRequests()`
- `PermissionService` 只有实例缓存和空方法

对比 `jetbrains-cc-gui`，当前缺少整个权限子系统：

- `PermissionRequest`
- `PermissionManager`
- `PermissionDecisionStore`
- `PermissionRequestWatcher`
- `PermissionDialogRouter`
- `ToolInterceptor`
- `DiffReviewService`
- `PermissionSessionRegistry`

结论：

- 这是当前和参考项目差距最大的后端模块之一。

### 2. Provider 架构只完成了 Claude 分支

当前项目虽有 `ProviderManager` 和部分前端 Provider UI，但 Java 后端实际只有 Claude 主线。

现状：

- `ProviderManager` 目前只是轻量切换和归一化
- 没有 `CodexSDKBridge`
- 没有 Codex history / usage / quota / provider-specific adapter
- 没有 provider 路由层

对比 `jetbrains-cc-gui`：

- 参考项目同时具备 `provider/claude/*` 与 `provider/codex/*`
- 还有更多 provider 配置管理类和消息转换类

结论：

- 当前项目还不能称为真正的多 Provider 架构。
- 现在更准确的描述是：前端为多 Provider 预留了 UI，后端仍是 Claude-only。

### 3. 很多 handler 仍缺失

当前项目 handler 覆盖面仍很小，主要是：

- 用户消息匿名 handler
- `HistoryHandler`
- `DiffHandler`
- `PermissionHandler` 占位

而参考项目还包含大量独立 handler：

- `SessionHandler`
- `SettingsHandler`
- `PromptHandler`
- `AgentHandler`
- `SkillHandler`
- `McpServerHandler`
- `TabHandler`
- `WindowEventHandler`
- `RewindHandler`
- `ContextHandler`
- `InputHistoryHandler`
- `DependencyHandler`
- `NodePathHandler`
- `ProjectConfigHandler`
- `UsagePushService`

结论：

- 当前项目的 handler 架构已经搭起来了。
- 但业务 handler 的覆盖度还很低，仍需要持续补面。

### 4. MCP / Prompt / Skill 体系只有结构，没有完整行为

当前项目有：

- [McpServerManager.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/settings/manager/McpServerManager.java)
- [PromptManager.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/settings/manager/PromptManager.java)

但整体仍比较薄，缺少：

- 后端 handler
- 文件扫描 / registry / 持久化
- Provider 侧实际注入
- 技能命令扫描体系

对比参考项目，当前还缺失：

- `SkillService`
- `CodexSkillService`
- `SlashCommandRegistry`
- `PromptManagerFactory`
- `ProjectPromptManager`
- `GlobalPromptManager`
- `SkillManager`
- `SessionTemplateService`
- `McpServerHandler`
- `CodexMcpServerHandler`

### 5. Daemon / common provider 层仍不完整

当前项目虽然有：

- `BaseSDKBridge`
- `ClaudeSDKBridge`

但以下类还不完整或不存在：

- [DaemonBridge.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/DaemonBridge.java) 目前是占位
- 没有 provider common 下更细的 request / result / callback / lite reader 体系

这说明当前 bridge 层主要解决“Claude SDK 能调用”，还没达到参考项目那种更细分的桥接中间层。

### 6. Settings 和 Manager 层刚起步

当前项目并不是旧文档里写的“多数硬编码默认值”了：

- [SettingsService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/settings/SettingsService.java) 已经基于 `PropertiesComponent` 做持久化
- `ProviderManager`、`WorkingDirectoryManager`、`McpServerManager` 已存在

但对比参考项目，当前还差：

- 更完整的 Provider 设置拆分
- Codex/Claude 分治配置
- Session template
- Prompt scope 管理
- 排序、导入导出、路径管理

结论：

- Settings 层已经脱离“纯桩”状态。
- 但距离参考项目的完整配置体系还有明显差距。

---

## 对比 `jetbrains-cc-gui` 的模块进度表

### A. 主链能力

| 能力 | 当前状态 | 判断 |
|---|---|---|
| Tool Window + JCEF + React | 已实现 | 已具备 |
| Claude SDK Node 桥接 | 已实现 | 已具备 |
| 流式消息展示 | 已实现 | 已具备 |
| 会话创建 / 发送 / 中断 | 已实现 | 已具备 |
| 历史索引与列表返回 | 已实现 | 部分具备 |
| Diff 展示 | 已实现 | 部分具备 |
| 权限审批闭环 | 未实现 | 缺失 |
| 多 Provider 路由 | 未实现 | 缺失 |
| Codex 后端 | 未实现 | 缺失 |
| Skills / MCP 后端体系 | 未实现 | 缺失 |

### B. 与参考项目的接近程度

| 模块 | 当前项目 | 相对参考项目判断 |
|---|---|---|
| UI / Webview | 完整度较高 | 接近 |
| Handler Core | 已抽象 | 接近 |
| Claude 会话主链 | 可用 | 中等接近 |
| 历史系统 | 局部闭环 | 差距较大 |
| 权限系统 | 占位 | 差距很大 |
| Provider 扩展 | Claude-only | 差距很大 |
| Settings 体系 | 初步可用 | 差距较大 |
| Skills / MCP | UI 有，后端弱 | 差距很大 |
| Diff 审批写入链 | 半实现 | 差距较大 |

---

## 当前开发阶段判断

如果把项目阶段粗分为 4 层：

1. 插件壳子
2. 单 Provider 聊天主链
3. IDE Agent 基础设施
4. 多 Provider + 权限 + 历史 + 技能 + 审计的完整产品

当前项目大致处于：

- **第 2 层已完成**
- **第 3 层进行中**
- **第 4 层刚开始搭骨架**

这也是为什么当前项目会呈现出一个比较明显的特征：

- 前端看起来已经很像完整产品
- 但后端很多系统还没进入真正的产品化实现

---

## 接下来该做什么

建议下一阶段不要继续横向铺新 UI，而是按后端闭环优先级推进。

### 第一优先级：权限系统闭环

原因：

- 这是参考项目里最关键的受控执行能力。
- 当前 diff、工具执行、未来 Agent 自动修改，都依赖权限系统。
- 没有这层，很多“会改代码”的能力都只能停留在演示状态。

建议任务：

1. 设计 `PermissionRequest`、待审批队列、会话级 registry。
2. 接通前端 `PermissionDialog` 与 Java 后端。
3. 把文件写入、命令执行、diff 应用动作统一接入权限判定。
4. 增加会话级 decision memory。

### 第二优先级：把历史系统升级为真实历史系统

原因：

- 当前历史更多是运行期索引，不是 provider-backed history。
- 参考项目的历史能力不仅是 UI 列表，而是历史恢复、搜索、导出、转换、subagent 记录等体系。

建议任务：

1. 增加 Claude 历史读取器、parser、index service。
2. 把当前 `HistoryIndexService` 从内存索引升级为“运行期索引 + provider 历史读取”双层模型。
3. 补 `deep_search_history` 的真实实现。
4. 评估是否引入 Codex 历史结构，避免后续重做。

### 第三优先级：补齐 session / handler 分层

原因：

- 当前匿名消息 handler 过重，`ChatWindowDelegate` 仍承担过多接线职责。
- 参考项目已经把会话、provider 路由、消息编排拆得更清楚。

建议任务：

1. 把用户消息处理从匿名 `MessageHandler` 提出为独立类。
2. 引入 provider router / session orchestrator 概念。
3. 为将来的 Codex 接入预留统一发送入口。

### 第四优先级：接 Codex Provider

原因：

- 前端已经明显带有双 Provider 设计意图。
- 当前后端如果不补上 Codex，很多 provider UI 只是摆设。

建议任务：

1. 新增 `provider/codex/*`
2. 统一 `BaseSDKBridge` 之上的 Provider 生命周期接口
3. 增加 Codex history / usage / model 配置对接

### 第五优先级：MCP / Skill / Prompt 后端闭环

原因：

- 当前这块主要还是 UI 和 manager 骨架。
- 一旦权限与 provider 主链稳住，这块会成为用户感知很强的差异化能力。

建议任务：

1. 先打通 MCP server 配置到 provider 调用链。
2. 再补 slash command / skill registry。
3. 最后做 prompt scope 与模板体系。

---

## 建议的下一阶段里程碑

### 里程碑 1：受控执行版 Claude-only Agent

目标：

- Claude 主链稳定
- 权限系统打通
- Diff 审批与写入闭环
- 历史恢复基础版可用

完成标准：

- 用户可以在 Tool Window 里安全地让 AI 读取、提议修改、查看 diff、确认应用
- 历史会话可以真实恢复，而不只是前端显示

### 里程碑 2：参考项目核心能力补齐

目标：

- 完整历史系统
- 更清晰的 session / handler 分层
- 配置体系扩展
- MCP / Prompt 后端可用

完成标准：

- 当前项目在 Claude 路线上接近 `jetbrains-cc-gui` 的核心使用体验

### 里程碑 3：双 Provider 产品化

目标：

- Codex Provider 接入
- Provider 路由统一
- Usage / quota / model 能力补齐

完成标准：

- 前端的多 Provider 设计与后端真实能力一致

---

## 本次整理后的结论

相对旧版文档，当前最需要修正的判断有三点：

1. `HistoryHandler` 不再是桩，已经有一批真实 history service，但仍不是完整历史系统。
2. `StreamMessageCoalescer` 不再是桩，已经有真实节流和心跳逻辑。
3. `SettingsService` 不再是“多数硬编码默认值”，已经基于 `PropertiesComponent` 做了基础持久化。

当前项目最准确的定位是：

**一个已经完成 Claude 聊天主链、正在补齐 IDE Agent 基础设施、目标对齐 `jetbrains-cc-gui` 的 IntelliJ 插件。**

它现在最缺的不是更多页面，而是：

- 权限系统
- 真实历史系统
- 多 Provider 后端
- MCP / Skill / Prompt 后端闭环

