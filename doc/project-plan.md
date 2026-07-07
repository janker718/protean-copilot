# Protean Copilot 项目思路与路线图

本文把 `Protean Copilot` 的产品思路、技术架构、MVP 范围和后续演进路线落到文档中。后续开发过程中，这份文档可以持续更新，作为项目方向和任务拆分的主入口。

## 项目定位

`Protean Copilot` 的目标不是做一个简单的 IDEA 聊天窗口，而是做一个基于 IntelliJ IDEA 的编程辅助工具，逐步演进成能理解项目、调用 IDE 能力、受控修改代码并验证结果的编程 Agent。

第一阶段先以 IntelliJ IDEA 插件形态落地：

- 提供 Tool Window 作为对话和任务控制入口。
- 提供 Action 作为从当前 IDE 上下文触发具体任务的入口。
- 逐步接入当前文件、选区、项目结构、Git diff、编译错误、测试结果等上下文。
- 后续再接入本地 Agent Runtime 或远端 LLM 服务。

## 核心用户场景

优先围绕真实开发工作流，而不是泛聊天：

- 解释当前选中的代码。
- 基于当前类生成单元测试。
- 根据编译错误或测试失败结果定位问题。
- 对 Git diff 做代码 review。
- 根据需求修改多个文件，并展示 diff 让用户确认。
- 分析项目结构、模块职责和调用链。
- 接入团队知识库、接口文档、日志平台或 CI/CD 结果。

## 总体架构

推荐架构分三层：

```text
IntelliJ IDEA Plugin
        |
        | 收集 IDE 上下文、展示 UI、展示 diff、执行用户确认后的修改
        v
Agent Runtime / Backend Service
        |
        | 任务规划、上下文裁剪、工具调用、模型请求、执行状态管理
        v
LLM / Embedding / Vector Store / External Tools
```

### IDEA 插件层

插件层负责和 IntelliJ IDEA 深度集成：

- Tool Window：对话、任务状态、历史记录、操作按钮。
- Actions：解释选区、生成测试、修复错误、Review diff。
- Editor API：读取当前编辑器、选区、光标位置、文档内容。
- VirtualFile / Project API：读取项目文件、目录、模块信息。
- PSI：理解类、方法、变量、引用关系，比纯文本更可靠。
- Notification：向用户反馈任务状态。
- Diff / Write Command：后续用于展示和应用代码修改。

### Agent Runtime 层

Agent Runtime 负责把用户意图变成可执行任务：

- 解析用户请求。
- 制定执行计划。
- 收集和裁剪上下文。
- 调用 LLM。
- 调用工具，例如读文件、查符号、运行测试、生成 diff。
- 管理多轮执行状态。
- 在写文件、运行命令、提交变更等动作前做权限控制。

Agent Runtime 可以先做成本地服务，也可以先做插件内的 Kotlin 服务类。建议不要一开始就把所有能力塞进 UI 代码里，避免后续难以扩展。

### 模型与工具层

这一层可以按阶段接入：

- LLM API：OpenAI、Anthropic、本地模型或企业模型。
- Embedding：用于项目语义检索。
- Vector Store：SQLite、Postgres pgvector、Qdrant、Milvus。
- 外部工具：Git、Gradle、Maven、测试框架、日志平台、CI 系统。

## 当前项目状态

当前已经完成 IDEA 插件骨架：

- Gradle 9 + `org.jetbrains.intellij.platform` 2.x。
- Java 21 编译目标。
- Kotlin 插件代码。
- `Protean Copilot` Tool Window。
- `Tools | Protean Copilot | Explain Selection` Action。
- Tool Window 已采用桥接模式：Swing UI 只负责展示，`ToolWindowBridge` 负责上下文采集、状态变化和后续 Agent 集成边界。
- `buildPlugin` 已验证可成功打包。

关键文件：

- [build.gradle.kts](/Users/janker/Documents/ProteanCopilot/build.gradle.kts)
- [plugin.xml](/Users/janker/Documents/ProteanCopilot/src/main/resources/META-INF/plugin.xml)
- [ProteanToolWindowPanel.kt](/Users/janker/Documents/ProteanCopilot/src/main/kotlin/com/protean/copilot/ui/ProteanToolWindowPanel.kt)
- [ExplainSelectionAction.kt](/Users/janker/Documents/ProteanCopilot/src/main/kotlin/com/protean/copilot/actions/ExplainSelectionAction.kt)
- [调试运行说明](./debug-and-run.md)

## MVP 范围

MVP 先完成一个可用的 IDEA 编程助手闭环：

1. 在 Tool Window 输入任务。
2. 插件能读取当前项目、当前文件、选区。
3. 插件能构造一次明确的上下文请求。
4. 调用 Agent Runtime 或 LLM。
5. 返回解释、建议或代码片段。
6. 对代码修改先展示 diff。
7. 用户确认后再写入文件。

MVP 不建议一开始就做完整自动 Agent。先把“上下文采集 -> 请求 -> 返回 -> 用户确认”的链路跑通，再扩展多步执行。

## 第一阶段任务拆分

### 1. 插件基础能力

- 完善 Tool Window UI：输入框、发送按钮、输出区域、任务状态。
- Action 支持读取当前选区和当前文件。
- 提供统一的 `IdeContext` 数据结构。
- 增加通知和错误展示。

### 2. 上下文采集

- 当前项目名称和根路径。
- 当前文件路径、语言、全文。
- 当前选区文本。
- 光标附近代码窗口。
- Git diff。
- 最近一次构建或测试输出。

### 3. Agent / LLM 接入

- 定义 `AgentClient` 接口。
- 先实现一个 mock client，便于 UI 和上下文调试。
- 再接入本地 HTTP Agent Runtime。
- 请求和响应都保留日志，方便排查问题。

### 4. Diff 与写文件

- 模型返回的修改不要直接写入。
- 先转换成 patch 或文件级 diff。
- 用 IDEA diff UI 展示。
- 用户确认后通过 Write Command 写入文件。
- 写入前后保留可回滚信息。

### 5. 验证闭环

- 支持运行 Gradle/Maven 测试。
- 读取失败输出。
- 将失败信息回传给 Agent。
- 允许 Agent 生成下一轮修复建议。

## 第二阶段能力

当 MVP 稳定后，再做项目级能力：

- 项目索引和相关文件召回。
- PSI 符号分析：类、方法、引用、调用关系。
- Git review：基于当前 diff 输出问题、风险和测试建议。
- 单测生成：识别测试框架和已有测试风格。
- 多文件修改：任务计划、分步执行、逐步确认。
- 任务历史：保存 prompt、上下文摘要、生成结果和最终 diff。

## 第三阶段能力

面向生产级 Agent：

- 权限模型：读文件、写文件、运行命令、网络访问分级授权。
- 审计日志：记录每次工具调用、文件修改、命令执行。
- 企业知识库：接口文档、研发规范、故障手册、组件库。
- 成本控制：上下文裁剪、缓存、模型选择、token 预算。
- 评测体系：用固定任务集评估修复成功率、误改率、编译通过率。
- Split Mode / Remote Development 兼容。

## 关键设计原则

### 1. IDE 上下文优先

编程辅助工具的价值来自 IDE 上下文，不只是模型能力。优先让插件准确知道用户在哪个项目、哪个文件、哪个选区、遇到什么错误。

### 2. 修改必须可控

任何写文件操作都应经过 diff 预览和用户确认。后续即使做 Agent 自动执行，也要保留权限边界、审计和回滚能力。

### 3. 先做明确任务，再做开放 Agent

优先做解释选区、生成测试、修复错误、Review diff 这类边界清晰的功能。开放式“帮我完成这个需求”应该放在基础设施稳定之后。

### 4. UI 与 Agent Runtime 解耦

Tool Window 不应该直接承担复杂任务编排。建议保留清晰接口：

```text
UI -> ContextCollector -> AgentClient -> ResultRenderer / DiffApplier
```

### 5. 可验证比会回答更重要

每次生成代码后，最终都要能回到编译、测试、静态检查这些验证手段。长期目标是让插件能形成“生成 -> 应用 -> 验证 -> 修复”的闭环。

## 建议代码模块规划

后续可以逐步拆出这些包：

```text
com.protean.copilot
  actions/          IDEA Action 入口
  bridge/           Tool Window UI 与业务/Agent Runtime 的桥接层
  ui/               Tool Window 与 Swing UI
  context/          IDE 上下文采集
  agent/            AgentClient、请求响应 DTO
  diff/             diff 构造、预览、应用
  settings/         API Key、模型、后端地址等配置
  execution/        构建、测试、命令执行
  telemetry/        日志、审计、调试信息
```

当前已有：

```text
actions/
bridge/
context/
notifications/
ui/
```

下一步建议先新增：

```text
agent/
```

## 下一步优先级

建议按这个顺序推进：

1. 做 `ContextCollector`，把当前项目、文件、选区封装成结构化对象。
2. 做 `AgentClient` 接口和 mock 实现。
3. Tool Window 支持展示 mock 响应。
4. `Explain Selection` Action 改成真正调用 `ContextCollector + AgentClient`。
5. 接入本地 HTTP Agent Runtime。
6. 做 diff 预览和用户确认。
7. 做 Git diff review。
8. 做单测生成。
9. 做测试运行和失败修复闭环。

## 文档维护约定

后续更新项目时，建议同步维护文档：

- 新增运行方式：更新 [debug-and-run.md](./debug-and-run.md)。
- 新增架构模块：更新本文件或拆到 `architecture.md`。
- 新增上下文策略：拆到 `context-engine.md`。
- 新增 Agent 执行流程：拆到 `agent-runtime.md`。
- 每完成一个阶段，在本文件的“当前项目状态”和“下一步优先级”里同步更新。
