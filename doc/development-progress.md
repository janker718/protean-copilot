# 开发进度快照

更新时间：2026-07-08

本文基于当前仓库中的实际代码结构、插件配置和最近一次本地编译结果整理，用于回答三个问题：

1. 现在已经完成到什么程度。
2. 哪些模块已经有骨架但还没形成闭环。
3. 下一步应该优先补哪一层。

## 当前总体状态

项目已经不再只是一个 IntelliJ 插件空壳，当前形态是：

- IntelliJ IDEA 插件
- JCEF WebView + React 前端
- Java 后端桥接
- Claude SDK Node 子进程桥接
- 初步的平台化骨架

当前实际规模：

| 范围 | 当前情况 |
|---|---|
| 插件语言 | Java |
| 构建系统 | Gradle Groovy DSL |
| IntelliJ Platform Gradle Plugin | `2.17.0` |
| 目标 IDEA | `2025.2.6.1` |
| Java 版本 | Java 21 toolchain |
| Java 源码 | `67` 个 `.java` 文件 |
| Webview 前端 | `webview/src` 下完整 React/Vite 工程 |
| 前端构建 | `buildWebview` Gradle 任务集成 Vite 构建 |
| 插件入口 | Tool Window + Action + Startup + StatusBar |

结论：

- 主链路已经具备“插件加载 -> 打开 Tool Window -> WebView 前后端通信 -> 调用 Claude SDK Bridge”的基础能力。
- 项目最近新增了一批平台化骨架，开始从“能聊天的插件”向“可持续扩展的 IDE Agent 容器”演进。
- 但历史、权限、MCP、Prompt、Provider 管理等层目前多数仍处于空实现或半实现阶段。

## 最近一次已验证结果

最近一次本地验证已通过：

- `./gradlew compileJava`

这说明以下内容至少在编译层面已经打通：

- 新增 `plugin.xml` 挂点
- `startup` 包
- `statusbar` 包
- `cache` 包
- `history` 包
- `settings/manager` 包
- `provider/common/SessionInfo`

注意：

- 这里的“通过”表示结构自洽、类和接口签名正确。
- 不等于这些模块已经具备完整产品行为。

## 已完成能力

### 1. IntelliJ 插件基础入口

当前 `plugin.xml` 已经不只是最小 Tool Window 注册，已经具备更完整的插件入口骨架：

- Tool Window：`ProteanToolWindow`
- 菜单 Action：`ExplainSelectionAction`
- 打开工具窗 Action：`OpenToolWindowAction`
- 通知组：`ProteanCopilot Notifications`
- Startup Activities：
  - `ProteanStartupActivity`
  - `BridgePrewarmActivity`
- Status Bar Widget：
  - `ProteanStatusBarWidgetFactory`
  - `ProteanStatusBarWidget`
- 可选特性配置：
  - `terminal-features.xml`
  - `java-features.xml`

相关文件：

- [plugin.xml](/Users/janker/Documents/ProteanCopilot/src/main/resources/META-INF/plugin.xml)
- [OpenToolWindowAction.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/actions/OpenToolWindowAction.java)
- [ProteanStartupActivity.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/startup/ProteanStartupActivity.java)
- [BridgePrewarmActivity.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/startup/BridgePrewarmActivity.java)
- [ProteanStatusBarWidgetFactory.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/statusbar/ProteanStatusBarWidgetFactory.java)

### 2. Tool Window 主体与 WebView 化

当前主形态仍然是 JCEF WebView，不是 Swing 面板：

- `ProteanToolWindow` 负责装配内容
- `ProteanChatWindow` 负责 Tool Window 运行时主体
- `WebviewInitializer` 负责 JCEF 初始化和 JS 桥接
- `WebviewWatchdog` 负责健康检查
- `ProteanToolWindowPanel` 仍然存在，但更像遗留 Swing 版本或调试面板

相关文件：

- [ProteanToolWindow.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/ui/ProteanToolWindow.java)
- [ProteanChatWindow.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/ui/toolwindow/ProteanChatWindow.java)
- [WebviewInitializer.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/ui/WebviewInitializer.java)
- [protean-chat.html](/Users/janker/Documents/ProteanCopilot/src/main/resources/html/protean-chat.html)

### 3. React Webview 前端

`webview` 目录已经是完整前端工程，而不是占位静态页：

- 聊天 UI
- 历史 UI
- 设置 UI
- 权限对话框 UI
- MCP 相关前端组件
- Usage / Context / Message / Tool blocks
- 多语言 i18n
- 多个 Vitest 测试文件

这意味着前端“展示层”和“交互层”准备度已经高于 Java 侧的后端能力。

相关目录：

- [webview/src](/Users/janker/Documents/ProteanCopilot/webview/src)
- [webview/package.json](/Users/janker/Documents/ProteanCopilot/webview/package.json)
- [webview/src/ARCHITECTURE.md](/Users/janker/Documents/ProteanCopilot/webview/src/ARCHITECTURE.md)

### 4. Java ↔ JavaScript 桥接主链

桥接主链已经基本成型：

- `HandlerContext`、`MessageDispatcher`、`MessageHandler` 已整理到 `handler/core`
- `ChatWindowDelegate` 负责将 Java 端处理器和前端消息桥接起来
- Java 侧通过 `executeJavaScript()` 把事件推回前端
- WebView 侧通过 JS 桥把消息发回 Java

这部分已经开始具备“后端能力注册中心”的雏形。

相关文件：

- [HandlerContext.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/core/HandlerContext.java)
- [MessageDispatcher.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/core/MessageDispatcher.java)
- [MessageHandler.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/core/MessageHandler.java)
- [ChatWindowDelegate.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/ui/ChatWindowDelegate.java)

### 5. Claude SDK 桥接

Claude Provider 仍然是当前主 Provider，相关主干已经存在：

- `BaseSDKBridge`
- `ClaudeSDKBridge`
- `DaemonBridge`
- `NodeDetector`
- `NodeDetectionResult`
- `claude-sdk-bridge.mjs`

说明项目在“如何把 IDE 事件转成 SDK 调用”这一层已经不是空白。

相关文件：

- [BaseSDKBridge.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/common/BaseSDKBridge.java)
- [ClaudeSDKBridge.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/claude/ClaudeSDKBridge.java)
- [NodeDetector.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/bridge/NodeDetector.java)
- [claude-sdk-bridge.mjs](/Users/janker/Documents/ProteanCopilot/src/main/resources/bridge/claude-sdk-bridge.mjs)

### 6. IDE 上下文采集

最近做过包结构整理，上下文模块已经落到更合理的位置：

- `handler/context/CurrentFile`
- `handler/context/IdeContext`
- `handler/context/IdeContextCollector`
- `handler/context/Selection`
- `EditorContextTracker`

这说明上下文采集不再是零散工具类，而是在往统一上下文模型走。

相关文件：

- [IdeContext.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/context/IdeContext.java)
- [IdeContextCollector.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/context/IdeContextCollector.java)
- [EditorContextTracker.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/ui/EditorContextTracker.java)

### 7. Diff 模块骨架

Diff 相关类已经整理到 `handler/diff`，说明“代码修改可视化确认”这条路线已经开了包级边界：

- `DiffHandler`
- `SimpleDiffDisplayHandler`
- `InteractiveDiffHandler`
- `InteractiveDiffManager`
- `DiffBrowserBridge`
- `DiffFileOperations`
- `InteractiveDiffRequest`
- `DiffResult`
- `DiffAction`

当前判断：骨架已在，闭环未完成。

相关目录：

- [handler/diff](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/diff)

### 8. 平台化骨架新增

这是当前项目相较前一阶段最重要的结构变化。

目前已经新增：

- `cache`
  - `SessionIndexCache`
  - `SessionIndexEntry`
- `history`
  - `HistoryMetadataService`
  - `HistoryIndexService`
  - `HistoryExportService`
- `settings/manager`
  - `ProviderManager`
  - `WorkingDirectoryManager`
  - `PromptManager`
  - `McpServerManager`
- `provider/common/SessionInfo`

这批文件的意义不是“功能已经完整”，而是：

- 会话索引开始有独立层
- 配置开始从 `SettingsService` 向 manager 层抬升
- Provider、Prompt、MCP、工作目录开始有明确落位
- 后面继续对齐 `jetbrains-cc-gui` 时，不需要再把所有逻辑堆到 `handler` 或 `ui`

相关文件：

- [SessionIndexCache.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/cache/SessionIndexCache.java)
- [HistoryMetadataService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/history/HistoryMetadataService.java)
- [ProviderManager.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/settings/manager/ProviderManager.java)
- [WorkingDirectoryManager.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/settings/manager/WorkingDirectoryManager.java)
- [McpServerManager.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/settings/manager/McpServerManager.java)

## 半实现 / 骨架态模块

下面这些模块虽然已经有文件或入口，但当前不能按“已完成功能”看待：

| 模块 | 当前情况 | 结论 |
|---|---|---|
| 权限系统 | `PermissionService`、`PermissionHandler` 仍偏桩实现 | 还没有真正的用户确认闭环 |
| 历史系统 | 新增了 `history` 和 `cache`，但主要仍是骨架 | 还没有完整的历史列表、恢复、删除、导出闭环 |
| Provider 管理 | `ProviderManager` 已有，但目前仍很薄 | 只是抽出入口，还未形成多 Provider 编排 |
| 工作目录管理 | `WorkingDirectoryManager` 已有 | 还未全面接管现有会话主链 |
| Prompt / MCP 管理 | `PromptManager`、`McpServerManager` 已建包 | 目前更像占位层 |
| Status Bar / Startup | 已注册并可编译 | 目前主要是骨架挂点，不是复杂行为 |
| 会话索引 | `SessionIndexCache`、`HistoryMetadataService` 已有 | 还未确认在真实发送链路里全面接入 |
| Diff 闭环 | 类较多 | 需要确认“展示 -> 确认 -> 应用/拒绝”是否真的打通 |
| Stream 合并 | `StreamMessageCoalescer` 已存在 | 需确认高频流更新是否已经稳定 |
| Settings 持久化 | `SettingsService`、`TabStateService` 已有实现 | 仍需验证实际覆盖范围与恢复效果 |

## 最近结构变化

当前仓库最近几次改动，已经能看出明确的架构收敛方向：

- `context/*` 迁移到 `handler/context/*`
- `handler` 核心接口迁移到 `handler/core/*`
- `diff/*` 迁移到 `handler/diff/*`
- 新增 `startup/*`
- 新增 `statusbar/*`
- 新增 `cache/*`
- 新增 `history/*`
- 新增 `settings/manager/*`
- `plugin.xml` 从单一 Tool Window 入口扩成“Tool Window + Startup + Status Bar + Optional Features”

这说明项目当前最重要的进展不是某一个业务功能，而是开始从“散点功能”变成“有层次的插件架构”。

## 当前阶段判断

如果按阶段来划分，当前项目大致处在：

- 已走出“纯插件脚手架”
- 已具备“WebView 聊天主链”
- 正在进入“平台骨架搭建期”

还没有进入的阶段：

- 完整历史管理期
- 完整权限治理期
- 多 Provider 编排期
- 稳定 Agent 执行闭环期

## 建议下一步

### P0：把新骨架接入现有主链

优先级最高的不是继续新建类，而是把新增骨架真正接进去：

1. 让 `ProviderManager` 接管当前 provider 来源。
2. 让 `WorkingDirectoryManager` 接管当前 session 的 cwd 来源。
3. 在会话发送、恢复、结束路径上接入 `HistoryMetadataService`。
4. 让 `SessionIndexCache` 至少能稳定记录最近会话。

### P1：补齐历史闭环

1. 基于 `HistoryIndexService` 做最小历史列表。
2. 打通历史恢复。
3. 打通 Markdown 导出。
4. 再考虑删除、搜索、标签、摘要。

### P2：补齐权限与修改闭环

1. 完善 `PermissionService` 和 `PermissionHandler`。
2. 确认 diff 展示链路。
3. 接上“用户确认 -> 应用/拒绝修改”。
4. 再做更复杂的工具调用授权。

### P3：再扩平台能力

1. `PromptManager` 做成真实项目级 Prompt 配置。
2. `McpServerManager` 接入真实来源。
3. 评估 `Codex Provider` 的接入边界。
4. 考虑 Java 侧测试和更稳定的集成验证。

## 文档维护建议

后续每次推进，建议同步更新：

- 本文件：记录“已完成 / 半实现 / 下一步”
- [project-plan.md](/Users/janker/Documents/ProteanCopilot/doc/project-plan.md)：记录路线图是否变化
- [architecture.md](/Users/janker/Documents/ProteanCopilot/doc/architecture.md)：记录模块边界变化
- [agent-runtime.md](/Users/janker/Documents/ProteanCopilot/doc/agent-runtime.md)：记录真实事件流和执行链
- [context-engine.md](/Users/janker/Documents/ProteanCopilot/doc/context-engine.md)：记录上下文采集策略变化
