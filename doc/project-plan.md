# Protean Copilot 项目思路与路线图

本文把 `Protean Copilot` 的产品思路、技术架构、当前状态和后续演进路线落到文档中。后续开发过程中，这份文档可以持续更新，作为项目方向和任务拆分的主入口。

---

## 项目定位

`Protean Copilot` 是一个基于 IntelliJ IDEA 的 AI 编程助手插件，目标是从聊天助手逐步演进为能理解项目、调用 IDE 能力、受控修改代码并验证结果的编程 Agent。

核心架构采用 **JCEF (Chromium Embedded Framework) WebView + React 前端 + Node.js 子进程 AI 桥接**，参照 `jetbrains-cc-gui` 的设计模式：

- **Tool Window** 通过 JCEF 内嵌 React 聊天界面，支持流式对话、思维链展示、工具调用可视化。
- **SDK Bridge** 通过 Node.js 子进程与 `@anthropic-ai/claude-code` SDK 通信，JSON-line 协议解耦前后端。
- **窗口函数桥接**：Java ↔ JavaScript 通过 `window` 对象上的函数双向调用，实现前后端通信。
- **多 Provider 架构**：`BaseSDKBridge` 抽象基类支持接入不同 AI 提供商的 SDK。

第一阶段以 IntelliJ IDEA 插件形态落地，后续可扩展为多平台插件 + 独立 Agent Runtime。

---

## 核心用户场景

优先围绕真实开发工作流，而不是泛聊天：

- 在工具窗口中与 AI 进行流式对话，实时查看思维链和工具调用过程。
- 解释当前选中的代码（`Explain Selection` Action）。
- 集成 IDE 上下文：当前文件、选区、项目结构、Git diff、编译错误。
- 多模型切换和自定义设置（权限模式、推理深度、工作目录）。
- 技能系统：通过斜杠命令触发预定义能力。
- MCP (Model Context Protocol) 工具集成。
- 会话历史管理、导出、跨会话恢复。
- 后续：diff 预览、受控文件写入、Git review、单测生成、验证闭环。

---

## 总体架构

```text
┌──────────────────────────────────────────────────────┐
│                  IntelliJ IDEA Plugin                 │
│                                                      │
│  ┌─────────────┐   ┌──────────────────────────────┐  │
│  │   Actions    │   │       Tool Window             │  │
│  │  Explain     │   │  ┌────────────────────────┐  │  │
│  │  Selection   │   │  │   JCEF Browser          │  │  │
│  │   etc.       │   │  │   (Chromium WebView)    │  │  │
│  └─────────────┘   │  │                          │  │  │
│                    │  │  ┌────────────────────┐  │  │  │
│                    │  │  │  React SPA          │  │  │  │
│                    │  │  │  (Vite + singlefile)│  │  │  │
│                    │  │  │                      │  │  │  │
│                    │  │  │  App.tsx             │  │  │  │
│                    │  │  │  ├─ ChatScreen       │  │  │  │
│                    │  │  │  ├─ Settings         │  │  │  │
│                    │  │  │  └─ History          │  │  │  │
│                    │  │  └────────────────────┘  │  │  │
│                    │  │                          │  │  │
│                    │  │  window.sendToJava()  ◄──┼──┼── JS → Java
│                    │  │  window.onContentDelta()─┼──┼── Java → JS
│                    │  └────────────────────────┘  │  │
│                    └──────────────────────────────┘  │
│                            │                         │
│  ┌─────────────────────────┴─────────────────────┐  │
│  │              Bridge Layer                       │  │
│  │                                                 │  │
│  │  SdkBridge  ───  ClaudeSDKBridge                │  │
│  │                   (extends BaseSDKBridge)        │  │
│  │                        │                        │  │
│  │              JSON-line IPC (stdin/stdout)        │  │
│  └─────────────────────────┼──────────────────────┘  │
│                            │                         │
│  ┌─────────────────────────┴──────────────────────┐  │
│  │           Session & Handler Layer                │  │
│  │                                                 │  │
│  │  ChatSession  ───  SessionCallbackAdapter       │  │
│  │  MessageDispatcher  ───  MessageHandler[]       │  │
│  │  SessionLifecycleManager                         │  │
│  │  EditorContextTracker                            │  │
│  └────────────────────────────────────────────────┘  │
│                                                      │
│  ┌────────────────────────────────────────────────┐  │
│  │           Context Layer                          │  │
│  │  IdeContextCollector  ───  IdeContext (record)   │  │
│  │  CurrentFile / Selection / PSI API               │  │
│  └────────────────────────────────────────────────┘  │
└──────────────────────┬───────────────────────────────┘
                       │
┌──────────────────────┴───────────────────────────────┐
│             Node.js Subprocess                        │
│                                                       │
│  bridge/claude-sdk-bridge.mjs                         │
│       │                                               │
│       └── @anthropic-ai/claude-code SDK               │
│              │                                        │
│              ├── query()      (streaming responses)   │
│              ├── interrupt()  (cancel)                │
│              ├── resume()     (history restore)       │
│              └── tools        (Bash, Read, Write, …)  │
└──────────────────────┬───────────────────────────────┘
                       │
                       v
              ┌─────────────────┐
              │   Claude API     │
              │   (Anthropic)    │
              └─────────────────┘
```

### 各层职责

**插件层 (Plugin Layer)**
- Tool Window: 对话 UI、流式渲染、权限对话框、MCP 工具面板、设置页。
- Actions: IDE 菜单入口（Explain Selection 等）。
- PSI/Editor API: 读取当前编辑器、选区、光标位置、文件内容。
- 通知系统: `ProteanNotifier` 向用户反馈任务状态。

**桥接层 (Bridge Layer)**
- `SdkBridge`: 统一的 SDK 入口，管理各 Provider 桥接实例的生命周期。
- `BaseSDKBridge`: 抽象基类，封装 Node.js 子进程的启动、JSON-line 协议解析、13 种流式事件分发。831 行核心代码。
- `ClaudeSDKBridge`: 继承 `BaseSDKBridge`，提供 Claude 特有配置（模型、脚本路径、SDK 可用性检查）。

**会话与处理层 (Session & Handler Layer)**
- `ChatSession`: 持有会话元数据（模型、工作目录、权限模式、推理深度）。
- `SessionCallbackAdapter`: 将 SDK 事件桥接到前端（`onContentDelta`、`onToolUse`、`updateMessages` 等 15 个回调方法）。
- `MessageDispatcher`: 消息路由，将前端 JSON 消息分发给注册的 `MessageHandler`。
- `SessionLifecycleManager`: 会话创建、历史加载、工作目录解析。

**前端层 (Webview)**
- React SPA 由 Vite + `vite-plugin-singlefile` 打包为单个 HTML 文件。
- `main.tsx`: 入口，注册 React Context Providers、心跳、主题初始化。
- `App.tsx`: 三视图路由（聊天、历史、设置）。
- `useWindowCallbacks.ts`: 约 770 行，注册所有 Java→JS 窗口函数。
- 组件: `ChatScreen`、`ChatInputBox`、`MarkdownBlock`、`PermissionDialog`、`MessageItem` 等 50+ 组件。
- 国际化: 10 种语言（en, zh, zh-TW, ja, ko, fr, es, pt-BR, ru, hi）。

**上下文层 (Context Layer)**
- `IdeContextCollector`: 通过 IntelliJ PSI API 收集结构化上下文。
- `IdeContext` (record): `projectName`, `projectBasePath`, `currentFile`, `selection`, `openFiles`。
- `EditorContextTracker`: 监听编辑器选区变化和文件切换，200ms 防抖推送前端。

---

## 当前项目状态

### 已完成

**构建系统**
- Gradle Groovy DSL (`build.gradle`, 130 行)。
- IntelliJ Platform Gradle Plugin 2.17.0。
- Java 21 toolchain。
- `buildWebview` Gradle 任务自动触发 `npm run build`。
- Webview Vite + `vite-plugin-singlefile` 打包为单 HTML 文件。

**插件核心 (35 个 Java 文件)**

| 包 | 核心类 | 状态 |
|---|---|---|
| `actions/` | `ExplainSelectionAction` | ✅ 已实现 |
| `bridge/` | `SdkBridge`, `ToolWindowBridge`, `ToolWindowBridgeView` | ✅ 已实现 |
| `context/` | `IdeContextCollector`, `IdeContext` (record), `CurrentFile`, `Selection` | ✅ 已实现 |
| `handler/` | `MessageDispatcher`, `HandlerContext`, `MessageHandler` | ✅ 已实现 |
| `handler/` | `HistoryHandler`, `PermissionHandler` | ⚪ 桩实现 |
| `notifications/` | `ProteanNotifier` | ✅ 已实现 |
| `permission/` | `PermissionService` | ⚪ 桩实现 |
| `provider/common/` | `BaseSDKBridge` (831 lines) | ✅ 核心实现 |
| `provider/claude/` | `ClaudeSDKBridge` | ✅ 已实现 |
| `provider/` | `DaemonBridge` | ⚪ 桩实现 |
| `session/` | `ChatSession`, `SessionCallbackAdapter`, `SessionLifecycleManager`, `SessionLoadService` | ✅ 已实现 |
| `session/` | `StreamMessageCoalescer` | ⚪ 桩实现 |
| `settings/` | `SettingsService`, `TabStateService` | ✅ 半实现 |
| `ui/` | `ProteanChatWindow` (931 lines), `ChatWindowDelegate` (350 lines) | ✅ 核心实现 |
| `ui/` | `WebviewInitializer` (204 lines), `WebviewWatchdog` | ✅ 已实现 |
| `ui/` | `EditorContextTracker`, `PendingCodeSnippetBuffer` | ✅ 已实现 |
| `util/` | `HtmlLoader`, `JsUtils`, `ThemeConfigService` | ✅ 已实现 |

**Webview React 前端**

| 层级 | 内容 | 规模 |
|---|---|---|
| 入口 | `main.tsx`, `App.tsx` (~1170 lines) | ✅ |
| 上下文 | 6 个 React Context (Messages, Session, UIState, Dialog, Subagent) | ✅ |
| Hooks | 30+ hooks，核心 15 个 (useWindowCallbacks ~770 lines, useStreamingMessages, 等) | ✅ |
| 组件 | 50+ 组件 (ChatScreen, ChatInputBox, MessageList, MarkdownBlock, PermissionDialog, ToolBlocks, Settings, History, MCP) | ✅ |
| i18n | 10 种语言 (en, zh, zh-TW, ja, ko, fr, es, pt-BR, ru, hi) | ✅ |
| 样式 | Less (app.less + component-specific) | ✅ |

**AI 桥接**
- `BaseSDKBridge`: JSON-line 协议、子进程管理、13 种流式事件分派。
- `ClaudeSDKBridge`: 对接 `@anthropic-ai/claude-code`。
- `bridge/claude-sdk-bridge.mjs`: Node.js 桥接脚本。

**JS 桥接**
- Java → JS: `CefBrowser.executeJavaScript()` 调用 `window.<function>(args)`。
- JS → Java: `window.sendToJava(msg)` → `console.log('__PROTEAN__:' + msg)` → `CefDisplayHandler` 拦截。
- `JsUtils`: 函数名校验正则 + 字符串安全转义。

### 未完成 / 待优化

| 模块 | 现状 | 优先级 |
|---|---|---|
| 持久化 Daemon 进程 | 每次请求独立启动 Node.js 子进程，5-10 秒初始化延迟 | 高 |
| 权限系统 | `PermissionHandler` / `PermissionService` 为桩 | 高 |
| 流合并 | `StreamMessageCoalescer` 为桩 | 中 |
| 历史加载 | `HistoryHandler` 为桩 | 中 |
| 配置持久化 | `SettingsService` 大部分返回硬编码默认值 | 中 |
| Codex Provider | 未实现 | 低 |
| JBCefJSQuery 桥接 | 当前用 console.log 桥接，生产构建中 `esbuild.drop: ['console']` 会断开 | 中 |
| 测试 | 无 Java 测试；webview 有少量 vitest 测试 | 中 |

### 关键文件索引

- [build.gradle](/Users/janker/Documents/ProteanCopilot/build.gradle)
- [plugin.xml](/Users/janker/Documents/ProteanCopilot/src/main/resources/META-INF/plugin.xml)
- [ProteanChatWindow.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/ui/toolwindow/ProteanChatWindow.java)
- [ProteanToolWindow.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/ui/ProteanToolWindow.java)
- [BaseSDKBridge.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/common/BaseSDKBridge.java)
- [ClaudeSDKBridge.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/claude/ClaudeSDKBridge.java)
- [SessionCallbackAdapter.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/SessionCallbackAdapter.java)
- [WebviewInitializer.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/ui/WebviewInitializer.java)
- [ChatWindowDelegate.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/ui/ChatWindowDelegate.java)
- [claude-sdk-bridge.mjs](/Users/janker/Documents/ProteanCopilot/src/main/resources/bridge/claude-sdk-bridge.mjs)
- [App.tsx](/Users/janker/Documents/ProteanCopilot/webview/src/App.tsx)
- [调试运行说明](./debug-and-run.md)

---

## MVP 范围

MVP 目标是完成一个可用的 IDEA 编程助手闭环，直接对标 `jetbrains-cc-gui` 的核心体验：

| # | 功能 | 状态 |
|---|---|---|
| 1 | 在 Tool Window 中与 AI 进行流式对话 | ✅ 已完成 |
| 2 | 插件能读取当前项目、当前文件、选区 | ✅ 已完成 (`IdeContextCollector`) |
| 3 | 插件构造结构化上下文请求 | ✅ 已完成 |
| 4 | 调用 AI SDK（流式响应、工具调用可视化） | ✅ 已完成 |
| 5 | 返回解释、建议或代码片段并流式展示 | ✅ 已完成 |
| 6 | 权限对话框（工具调用确认） | ⚪ 前端 UI 已有，后端桩 |
| 7 | 会话历史记录与恢复 | ⚪ 前端 UI 已有，后端桩 |
| 8 | 对代码修改先展示 diff | 🔜 待实现 |
| 9 | 用户确认后再写入文件 | 🔜 待实现 |
| 10 | 多语言 i18n | ✅ 已完成 (10 种语言) |

---

## 第一阶段任务拆分

### 1. 插件基础能力 ✅

- ✅ Tool Window UI: JCEF Chromium WebView + React 前端。
- ✅ Action 支持读取当前选区和当前文件。
- ✅ 统一的 `IdeContext` 数据结构。
- ✅ 通知系统 `ProteanNotifier`。

### 2. 上下文采集 ✅

- ✅ 当前项目名称和根路径。
- ✅ 当前文件路径、语言、全文。
- ✅ 当前选区文本。
- ✅ `EditorContextTracker` 实时选区监控。
- 🔜 Git diff。
- 🔜 最近一次构建或测试输出。

### 3. AI 桥接 ✅

- ✅ `BaseSDKBridge` 抽象基类（子进程管理、JSON-line 协议）。
- ✅ `ClaudeSDKBridge` Claude SDK 具体实现。
- ✅ `bridge/claude-sdk-bridge.mjs` Node.js 桥接脚本。
- ✅ 流式事件分派（13 种事件类型）。
- ✅ 前端回调适配器（15 个流式回调方法）。
- 🔜 Daemon 持久化进程（减少启动延迟）。
- 🔜 Codex Provider 实现。

### 4. Diff 与写文件 🔜

- 模型返回的修改先转换成 diff 预览。
- 用 IDEA diff UI 展示。
- 用户确认后通过 Write Command 写入文件。
- 写入前后保留可回滚信息。

### 5. 验证闭环 🔜

- 支持运行 Gradle/Maven 测试。
- 读取失败输出并回传给 AI。
- Agent 生成修复建议的循环。

---

## 第二阶段能力

当 MVP 稳定后：

- **Daemon 持久化进程**: 迁移到 `docs/feat/daemon-architecture-refactor.md` 描述的架构，消除每次请求 5-10s 的 SDK 初始化延迟。
- **项目索引**: PSI 符号分析（类、方法、引用、调用关系），语义检索。
- **Git Review**: 基于当前 diff 输出问题、风险和测试建议。
- **单测生成**: 识别测试框架和已有测试风格，自动生成单元测试。
- **多文件修改**: 任务计划、分步执行、逐步确认，展示 diff 链。
- **任务历史**: 保存 prompt、上下文摘要、生成结果和最终 diff。
- **Slash Commands**: 技能系统，可扩展的自定义命令。

---

## 第三阶段能力

面向生产级 Agent：

- **权限模型**: 读文件、写文件、运行命令、网络访问分级授权。完善 `PermissionService` 和 `PermissionHandler`。
- **审计日志**: 记录每次工具调用、文件修改、命令执行。
- **企业知识库**: 接口文档、研发规范、故障手册、组件库。
- **成本控制**: 上下文裁剪、缓存、模型选择、token 预算。
- **评测体系**: 用固定任务集评估修复成功率、误改率、编译通过率。
- **Split Mode / Remote Development** 兼容。
- **Codex Provider**: 接入 OpenAI Codex CLI，对标参考项目的双 Provider 架构。

---

## 关键设计原则

### 1. JCEF 桥接可靠性优先

Java ↔ JavaScript 通信是用户体验的基础。当前使用 `console.log` 前缀拦截 + `executeJavaScript()` 的直接注入方式，需注意：
- 生产构建中 `esbuild.drop: ['console']` 会断开 JS → Java 通道。
- 应迁移到 `JBCefJSQuery` 方案（参考项目已迁移），提供类型安全的双向通信。
- 所有 JS 函数名需通过 `SAFE_JS_NAME` 正则校验，参数需经过 `JsUtils.escapeJs()` 转义。

### 2. 流式响应一致性

`SessionCallbackAdapter` 提供 15 个流式回调方法，前端通过 `useStreamingMessages` hook 处理。需保证：
- 流开始/结束信号成对出现（`onStreamStart` / `onStreamEnd`）。
- `StreamMessageCoalescer`（目前为桩）应实现批量合并，减少 WebView 推送频率。
- `WebviewWatchdog` 用心跳机制保护前端健康，45 秒无响应触发恢复流程。

### 3. 进程生命周期管理

`BaseSDKBridge` 管理 Node.js 子进程，需注意：
- 守护线程持续读取 stdout，按 JSON-line 解析事件。
- 写入锁保证 `stdin` 的线程安全。
- `CountDownLatch` 实现优雅关闭（30 秒超时强制终止）。
- `SdkBridge.cleanupAllProcesses()` 在工具窗口释放时调用。

### 4. IDE 上下文优先

编程辅助工具的价值来自 IDE 上下文，不只是模型能力：
- `IdeContextCollector` 通过 PSI API 获取结构化信息。
- `EditorContextTracker` 实时监听，200ms 防抖。
- 上下文应优先包含项目信息、当前文件、光标位置、编译错误。

### 5. 修改必须可控

任何写文件操作都应经过 diff 预览和用户确认。后续即使做 Agent 自动执行，也要保留权限边界、审计和回滚能力。

### 6. UI 与 Agent Runtime 解耦

```text
Tool Window (JCEF + React)  →  SdkBridge  →  BaseSDKBridge  →  Node.js 子进程  →  LLM API
```

插件层不应该直接承担复杂任务编排。每层职责明确，接口清晰。

### 7. 先做明确任务，再做开放 Agent

优先做解释选区、生成测试、修复错误、Review diff 这类边界清晰的功能。开放式"帮我完成这个需求"放在基础设施稳定之后。

---

## 代码模块规划

当前实际包结构（35 个 Java 文件）：

```text
com.protean.copilot
  actions/            ExplainSelectionAction
  bridge/             SdkBridge, ToolWindowBridge, ToolWindowBridgeView
  context/            IdeContext, CurrentFile, Selection, IdeContextCollector
  handler/            MessageHandler, MessageDispatcher, HandlerContext,
                      HistoryHandler (桩), PermissionHandler (桩)
  notifications/      ProteanNotifier
  permission/         PermissionService (桩)
  provider/
    claude/           ClaudeSDKBridge
    common/           BaseSDKBridge
                      DaemonBridge (桩)
  session/            ChatSession, SessionCallbackAdapter,
                      SessionLifecycleManager, SessionLoadService,
                      StreamMessageCoalescer (桩)
  settings/           SettingsService, TabStateService
  ui/
    toolwindow/       ProteanChatWindow, PendingCodeSnippetBuffer
    ProteanToolWindow, ChatWindowDelegate, WebviewInitializer,
    WebviewWatchdog, EditorContextTracker,
    ProteanToolWindowPanel (遗留 Swing UI)
  util/               HtmlLoader, JsUtils, ThemeConfigService
```

需要拆分的模块：

- `permission/` — 当前只有一个桩类，需扩展为完整的权限决策系统。
- `handler/` — `HistoryHandler` 和 `PermissionHandler` 需从桩升级为真实实现。
- `provider/codex/` — 新增 Codex Provider（参考 `jetbrains-cc-gui` 的 `CodexSDKBridge`）。
- `diff/` — 新增 diff 构造、预览、应用模块。
- `execution/` — 新增构建、测试、命令执行模块。

---

## 下一步优先级

建议按这个顺序推进：

1. **修复 `esbuild.drop: ['console']` 导致的生产桥接断开**（迁移到 `JBCefJSQuery`）。
2. **实现 Daemon 持久化进程** — 消除每次请求的 5-10s 启动延迟。参考 `jetbrains-cc-gui/docs/feat/daemon-architecture-refactor.md`。
3. **完善权限系统** — `PermissionService` 和 `PermissionHandler` 从桩升级为真实实现。
4. **实现历史会话** — `HistoryHandler` 从桩升级，支持会话保存和恢复。
5. **`StreamMessageCoalescer` 实现** — 批量合并流式更新以降低 WebView 推送频率。
6. **配置持久化** — `SettingsService` 从硬编码升级为读写 IDEA 设置存储。
7. **diff 预览和应用** — 新增 `diff/` 包，集成 IDEA Diff UI。
8. **Git review** — 基于当前 diff 进行分析。
9. **单测生成** — 识别测试框架和风格。
10. **Codex Provider** — 新增 `CodexSDKBridge`，实现多 Provider 切换。

---

## 文档维护约定

后续更新项目时，建议同步维护文档：

- 新增运行方式：更新 [debug-and-run.md](./debug-and-run.md)。
- 新增架构模块：更新本文件"代码模块规划"和"当前项目状态"节。
- 新增上下文策略：拆到 `context-engine.md`。
- 新增 Agent 执行流程：拆到 `agent-runtime.md`。
- 重大架构变更：新增 `docs/feat/<feature-name>.md` 格式的设计文档。
- 每完成一个阶段，在本文件的"当前项目状态"和"下一步优先级"里同步更新。
