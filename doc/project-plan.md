# Protean Copilot 项目思路与路线图

`Protean Copilot` 的产品思路、技术架构、当前状态和后续演进路线的总入口。子文档按维护约定拆分：

- [architecture.md](./architecture.md) — 总体架构、各层职责、设计原则、代码模块规划
- [context-engine.md](./context-engine.md) — IDE 上下文采集策略
- [agent-runtime.md](./agent-runtime.md) — Agent 执行流程与流式事件体系
- [debug-and-run.md](./debug-and-run.md) — 调试和运行说明

---

## 项目定位

`Protean Copilot` 是一个基于 IntelliJ IDEA 的 AI 编程助手插件，目标是从聊天助手逐步演进为能理解项目、调用 IDE 能力、受控修改代码并验证结果的编程 Agent。

核心架构采用 **JCEF (Chromium Embedded Framework) WebView + React 前端 + Node.js 子进程 AI 桥接**，参照 `jetbrains-cc-gui` 的设计模式：

- **Tool Window** 通过 JCEF 内嵌 React 聊天界面，支持流式对话、思维链展示、工具调用可视化。
- **SDK Bridge** 通过 Node.js 子进程与 `@anthropic-ai/claude-code` SDK 通信，JSON-line 协议解耦前后端。
- **JBCefJSQuery 桥接**：Java ↔ JavaScript 通过 `window` 对象上的函数双向调用。
- **多 Provider 架构**：`BaseSDKBridge` 抽象基类支持接入不同 AI 提供商的 SDK。

---

## 核心用户场景

优先围绕真实开发工作流：

- 在工具窗口中与 AI 进行流式对话，实时查看思维链和工具调用过程。
- 解释当前选中的代码（`Explain Selection` Action）。
- 集成 IDE 上下文：当前文件、选区、项目结构、Git diff、编译错误。
- 多模型切换和自定义设置（权限模式、推理深度、工作目录）。
- 技能系统：通过斜杠命令触发预定义能力。
- MCP (Model Context Protocol) 工具集成。
- 会话历史管理、导出、跨会话恢复。
- 后续：diff 预览、受控文件写入、Git review、单测生成、验证闭环。

---

## 当前项目状态

### 已完成

| 类别 | 内容 | 状态 |
|---|---|---|
| 构建系统 | Gradle Groovy DSL, Java 21, IntelliJ 2025.2.6.1 | ✅ |
| Webview 集成 | `buildWebview` Gradle 任务自动构建 React SPA | ✅ |
| 插件核心 | 35 个 Java 类（actions, bridge, context, handler, provider, session, settings, ui, util, i18n） | ✅ |
| Webview 前端 | 50+ React 组件, 30+ hooks, 10 种语言 i18n | ✅ |
| AI 桥接 | `BaseSDKBridge` (831 lines), `ClaudeSDKBridge`, JSON-line IPC | ✅ |
| JS 桥接 | `JBCefJSQuery` (JS→Java), `executeJavaScript()` (Java→JS) | ✅ |
| 流式事件 | 13 种事件类型, 15 个回调方法 | ✅ |
| 上下文 | `IdeContextCollector`, `EditorContextTracker` | ✅ |
| 国际化 | Java `DynamicBundle` (EN/ZH/ZH_TW) + Webview 10 语言 | ✅ |

### 未完成 / 待优化

| 模块 | 现状 | 优先级 |
|---|---|---|
| 权限系统 | `PermissionHandler` / `PermissionService` 为桩 | 高 |
| 流合并 | `StreamMessageCoalescer` 为桩 | 中 |
| 历史加载 | `HistoryHandler` 为桩 | 中 |
| 配置持久化 | `SettingsService` 大部分返回硬编码默认值 | 中 |
| Codex Provider | 未实现 | 低 |
| 测试 | 无 Java 测试；webview 有少量 vitest 测试 | 中 |

详细模块清单见 [architecture.md#代码模块规划](./architecture.md#代码模块规划)。

---

## MVP 范围

| # | 功能 | 状态 |
|---|---|---|
| 1 | 在 Tool Window 中与 AI 进行流式对话 | ✅ |
| 2 | 插件读取当前项目、当前文件、选区 | ✅ |
| 3 | 构造结构化上下文请求 | ✅ |
| 4 | 调用 AI SDK（流式响应、工具调用可视化） | ✅ |
| 5 | 返回解释、建议或代码片段并流式展示 | ✅ |
| 6 | 权限对话框（工具调用确认） | ⚪ 前端 UI 已有，后端桩 |
| 7 | 会话历史记录与恢复 | ⚪ 前端 UI 已有，后端桩 |
| 8 | 对代码修改先展示 diff | 🔜 |
| 9 | 用户确认后再写入文件 | 🔜 |
| 10 | 多语言 i18n | ✅ |

---

## 第一阶段任务拆分

### 1. 插件基础能力 ✅
- ✅ JCEF WebView + React 前端 Tool Window。
- ✅ Action 读取当前选区和当前文件。
- ✅ 统一 `IdeContext` 数据结构。
- ✅ 通知系统 `ProteanNotifier`。

### 2. 上下文采集 ✅
- ✅ 项目名称/路径、文件/选区信息。
- ✅ `EditorContextTracker` 实时选区监控。
- 🔜 Git diff、构建/测试输出。

### 3. AI 桥接 ✅
- ✅ `BaseSDKBridge` / `ClaudeSDKBridge` / MJS 桥接脚本。
- ✅ 13 种流式事件 + 15 个前端回调。
- ✅ Daemon SDK 预加载（prewarm） — 首响应 < 2s。
- ✅ `JBCefJSQuery` 原生 JCEF IPC 桥接。
- 🔜 Codex Provider。

### 4. Diff 与写文件 🔜

### 5. 验证闭环 🔜

---

## 第二阶段能力

- ~~Daemon 持久化进程~~ ✅ 已通过 SDK prewarm 实现。
- **项目索引** — PSI 符号分析、语义检索。
- **Git Review** — 基于 diff 输出问题和测试建议。
- **单测生成** — 识别测试框架和风格。
- **多文件修改** — 任务计划 + diff 链展示。
- **任务历史** — 保存 prompt、摘要、生成结果。
- **Slash Commands** — 可扩展的自定义命令系统。

---

## 第三阶段能力

- **权限模型**: 分级授权（读/写/执行/网络）。
- **审计日志**: 记录每次工具调用和文件修改。
- **企业知识库**: 接口文档、研发规范、故障手册。
- **成本控制**: 上下文裁剪、缓存、token 预算。
- **评测体系**: 修复成功率、误改率、编译通过率。
- **Split Mode / Remote Development** 兼容。
- **Codex Provider**: 双 Provider 架构。

---

## 下一步优先级

1. ✅ ~~实现 Daemon 持久化进程~~ → [daemon-prewarm.md](./feat/daemon-prewarm.md)
2. ✅ ~~修复 JBCefJSQuery 桥接~~ — 已从 console.log 迁移到原生 JCEF IPC
3. **完善权限系统** — `PermissionService` + `PermissionHandler` 从桩升级。
4. **实现历史会话** — `HistoryHandler` 从桩升级。
5. **`StreamMessageCoalescer` 实现** — 批量合并流式更新。
6. **配置持久化** — `SettingsService` 读写 IDEA 设置存储。
7. **diff 预览和应用** — 新增 `diff/` 包。
8. **Git review** — 基于 diff 分析。
9. **单测生成** — 识别测试框架。
10. **Codex Provider** — 多 Provider 切换。

---

## 文档索引

| 文档 | 说明 |
|---|---|
| [project-plan.md](./project-plan.md) | 本文件 — 项目总入口 |
| [architecture.md](./architecture.md) | 总体架构、设计原则、代码模块 |
| [context-engine.md](./context-engine.md) | IDE 上下文采集策略 |
| [agent-runtime.md](./agent-runtime.md) | Agent 执行流程与流式事件 |
| [debug-and-run.md](./debug-and-run.md) | 调试和运行说明 |
| [feat/daemon-prewarm.md](./feat/daemon-prewarm.md) | Daemon SDK 预加载设计 |

> 重大架构变更请新增 `docs/feat/<feature-name>.md` 格式的设计文档。
