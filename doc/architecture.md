# Protean Copilot 技术架构

本文档描述 `Protean Copilot` 的总体架构、各层职责、关键设计原则和代码模块规划。项目总览请参见 [project-plan.md](./project-plan.md)。

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

---

## 各层职责

### 插件层 (Plugin Layer)

- **Tool Window**: 对话 UI、流式渲染、权限对话框、MCP 工具面板、设置页。
- **Actions**: IDE 菜单入口（Explain Selection 等）。
- **PSI/Editor API**: 读取当前编辑器、选区、光标位置、文件内容。
- **通知系统**: `ProteanNotifier` 向用户反馈任务状态。

核心类：
- [ProteanChatWindow.java](../src/main/java/com/protean/copilot/ui/toolwindow/ProteanChatWindow.java) — 主窗口（931 行），协调 JCEF 浏览器、会话、处理器、看门狗。
- [ProteanToolWindow.java](../src/main/java/com/protean/copilot/ui/ProteanToolWindow.java) — `ToolWindowFactory` 入口。
- [WebviewInitializer.java](../src/main/java/com/protean/copilot/ui/WebviewInitializer.java) — JCEF 浏览器创建、JS 桥接注入。
- [ChatWindowDelegate.java](../src/main/java/com/protean/copilot/ui/ChatWindowDelegate.java) — 处理器编排（350 行）。

### 桥接层 (Bridge Layer)

- `SdkBridge`: 统一的 SDK 入口，管理各 Provider 桥接实例的生命周期。
- `BaseSDKBridge`: 抽象基类，封装 Node.js 子进程的启动、JSON-line 协议解析、13 种流式事件分发（831 行核心代码）。
- `ClaudeSDKBridge`: 继承 `BaseSDKBridge`，提供 Claude 特有配置（模型、脚本路径、SDK 可用性检查）。
- `bridge/claude-sdk-bridge.mjs`: Node.js 桥接脚本，对接 `@anthropic-ai/claude-code` SDK。

**JS 桥接**（Java ↔ JCEF WebView）：

- **JS → Java**: `JBCefJSQuery` 原生 JCEF IPC，前端调用 `window.sendToJava(msg)`。
- **Java → JS**: `CefBrowser.executeJavaScript()` 调用 `window.<function>(args)`，函数名通过 `SAFE_JS_NAME` 正则校验，参数经 `JsUtils.escapeJs()` 转义。

### 会话与处理层 (Session & Handler Layer)

- `ChatSession`: 持有会话元数据（模型、工作目录、权限模式、推理深度）。
- `SessionCallbackAdapter`: 将 SDK 事件桥接到前端（`onContentDelta`、`onToolUse`、`updateMessages` 等 15 个回调方法）。
- `MessageDispatcher`: 消息路由，将前端 JSON 消息分发给注册的 `MessageHandler`。
- `SessionLifecycleManager`: 会话创建、历史加载、工作目录解析。
- `WebviewWatchdog`: 心跳监控（45 秒超时 → 先 reload HTML → 再重建浏览器）。

### 前端层 (Webview)

- React SPA 由 Vite + `vite-plugin-singlefile` 打包为单个 HTML 文件。
- `main.tsx`: 入口，注册 React Context Providers、心跳（5 秒间隔）、主题初始化。
- `App.tsx`: 三视图路由（聊天、历史、设置），约 1170 行。
- `useWindowCallbacks.ts`: 约 770 行，注册所有 Java→JS 窗口函数。
- 组件: `ChatScreen`、`ChatInputBox`、`MarkdownBlock`、`PermissionDialog`、`MessageItem` 等 50+ 组件。
- 国际化: 10 种语言（en, zh, zh-TW, ja, ko, fr, es, pt-BR, ru, hi）。
- [Webview 架构文档](../webview/src/ARCHITECTURE.md)

### 上下文层 (Context Layer)

- `IdeContextCollector`: 通过 IntelliJ PSI API 收集结构化上下文。
- `IdeContext` (record): `projectName`, `projectBasePath`, `currentFile`, `selection`, `openFiles`。
- `EditorContextTracker`: 监听编辑器选区变化和文件切换，200ms 防抖推送前端。

详细上下文策略见 [context-engine.md](./context-engine.md)。

---

## 关键设计原则

### 1. JCEF 桥接可靠性优先

Java ↔ JavaScript 通信是用户体验的基础。已迁移到 `JBCefJSQuery`（IntelliJ 原生 JCEF IPC）替代 `console.log` 拦截方案：
- 生产构建中不再依赖 `console` 对象。
- 所有 JS 函数名通过 `SAFE_JS_NAME` 正则校验，参数经 `JsUtils.escapeJs()` 转义。
- `callJavaScript()` 始终在 EDT 上执行。

### 2. 流式响应一致性

`SessionCallbackAdapter` 提供 15 个流式回调方法，前端通过 `useStreamingMessages` hook 处理：
- 流开始/结束信号成对出现（`onStreamStart` / `onStreamEnd`）。
- `StreamMessageCoalescer`（目前为桩）应实现批量合并，减少 WebView 推送频率。
- `WebviewWatchdog` 用心跳机制保护前端健康。

### 3. 进程生命周期管理

`BaseSDKBridge` 管理 Node.js 子进程：
- 守护线程持续读取 stdout，按 JSON-line 解析事件。
- 写入锁保证 `stdin` 的线程安全。
- `CountDownLatch` 实现优雅关闭（30 秒超时强制终止）。
- `SdkBridge.cleanupAllProcesses()` 在工具窗口释放时调用。

### 4. IDE 上下文优先

编程辅助工具的价值来自 IDE 上下文：
- `IdeContextCollector` 通过 PSI API 获取结构化信息。
- `EditorContextTracker` 实时监听，200ms 防抖。
- 上下文优先包含项目信息、当前文件、光标位置、编译错误。

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
  i18n/               ProteanCopilotBundle
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

需要新增的模块：

- `permission/` — 当前只有一个桩类，需扩展为完整的权限决策系统。
- `handler/` — `HistoryHandler` 和 `PermissionHandler` 需从桩升级为真实实现。
- `provider/codex/` — 新增 Codex Provider。
- `diff/` — 新增 diff 构造、预览、应用模块。
- `execution/` — 新增构建、测试、命令执行模块。
