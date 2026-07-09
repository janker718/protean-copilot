# 开发进度快照

更新时间：2026-07-09

本文档基于两个仓库当前代码状态整理：

- 当前项目：`/Users/janker/Documents/ProteanCopilot`
- 参考项目：`/Users/janker/Documents/code/github/jetbrains-cc-gui`

目的：

1. 梳理当前项目和参考项目的结构对应关系。
2. 说明哪些能力已经对齐，哪些还只是部分复刻。
3. 记录最近一轮 Claude history 改造后的真实进度。
4. 给出下一阶段更合理的推进顺序。

---

## 一句话结论

`ProteanCopilot` 现在已经不是单纯的 Claude 聊天窗原型，而是一个已经具备：

- IntelliJ 插件壳
- JCEF + React WebView
- Claude provider 主链
- handler/core 分发层
- session 生命周期骨架
- history 子系统第一轮 provider 化
- permission 子系统第一轮接线

的可编译原型。

和 `jetbrains-cc-gui` 相比，当前项目已经开始对齐参考项目的目录边界和职责拆分，但整体仍明显停留在：

- `Claude-only`
- `单项目骨架已形成`
- `多 Provider / 深权限 / 深历史 / 技能体系未完成`

的阶段。

---

## 当前客观规模

| 维度 | ProteanCopilot | jetbrains-cc-gui |
|---|---:|---:|
| Java 文件数 | 112 | 268 |
| Claude provider 目录 | 已形成 | 完整 |
| Codex provider 目录 | 无 | 完整 |
| history handler/history/provider 三层 | 已形成 | 完整 |
| permission 子系统 | 已形成第一轮骨架 | 完整 |
| session 子模块拆分深度 | 中等 | 高 |

已验证：

- `./gradlew compileJava`

未在本次整理中重新验证：

- `runIde`
- WebView 交互级联
- 历史 UI 的完整回归

因此文档里的“已实现”表示：

- 代码存在
- 依赖接线打通
- 编译通过

不表示行为已经和参考项目完全等价。

---

## 结构对照

### 当前项目已经对齐的主目录

`ProteanCopilot` 当前已经具备这些与参考项目同类的核心边界：

- `bridge`
- `cache`
- `handler`
- `handler/core`
- `handler/context`
- `handler/diff`
- `handler/history`
- `handler/provider`
- `history`
- `permission`
- `provider/common`
- `provider/claude`
- `session`
- `settings`
- `startup`
- `ui/toolwindow`
- `util`

这说明当前项目已经从“功能全堆在窗口类里”的阶段，进入了“按子系统分层”的阶段。

### 仍明显缺失的主目录能力

相对参考项目，当前项目还没有或明显偏薄：

- `provider/codex`
- `skill`
- `service` 大量平台服务层
- `terminal`
- `watcher`
- 更完整的 `action/*`
- 更完整的 `ui/detached`

结论：

- 目录结构已经开始像参考项目。
- 但完整产品所需的横向子系统还没有铺满。

---

## 当前已对齐或接近对齐的部分

### 1. 插件入口和 WebView 主壳

当前项目已经具备完整的 IntelliJ 插件基础入口，包括：

- tool window
- startup activity
- status bar
- JCEF 页面初始化
- React/Vite 单文件打包接入

这部分和参考项目在方向上已经一致，差距主要在附加窗口、更多 action、更多运行时管理细节，不在主骨架。

### 2. handler/core 分发模式

当前项目已经抽出了：

- [HandlerContext.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/core/HandlerContext.java)
- [MessageHandler.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/core/MessageHandler.java)
- [BaseMessageHandler.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/core/BaseMessageHandler.java)
- [MessageDispatcher.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/core/MessageDispatcher.java)

这一层已经不是参考项目之外的自创组织，而是在向 `jetbrains-cc-gui` 的 handler 体系靠拢。

当前差距不在“有没有 core”，而在“handler 覆盖面不够广”。

### 3. Claude provider 主链

当前项目已经具备 Claude 发送主链：

- [BaseSDKBridge.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/common/BaseSDKBridge.java)
- [ClaudeSDKBridge.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/claude/ClaudeSDKBridge.java)
- Node bridge 资源文件
- session send / callback / lifecycle 接线

这部分说明当前项目的主工作流已经脱离 demo。

相对参考项目，差距主要在：

- daemon 协调层更薄
- provider 内部子服务拆分更少
- Codex 分支尚未引入

### 4. session 主链

当前项目已有：

- [ChatSession.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/ChatSession.java)
- [SessionSendService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/SessionSendService.java)
- [SessionLifecycleManager.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/SessionLifecycleManager.java)
- [SessionCallbackAdapter.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/SessionCallbackAdapter.java)
- [StreamMessageCoalescer.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/StreamMessageCoalescer.java)
- [MessageParser.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/session/MessageParser.java)

和参考项目相比，当前 session 层已经具备“发送、回调、聚合、恢复”的基本链路，但还没有进一步拆到：

- `SessionProviderRouter`
- `SessionMessageOrchestrator`
- `ReplayDeduplicator`
- `MessageMerger`
- Claude/Codex 分治消息处理器

判断：

- 当前 session 层可运行。
- 参考项目的 session 层更产品化。

---

## 最近已经完成的重点进展

### 1. Claude history provider 侧第一轮补齐

这轮已经新增：

- [ClaudeHistoryReader.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/claude/ClaudeHistoryReader.java)
- [ClaudeHistoryParser.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/claude/ClaudeHistoryParser.java)
- [ClaudeHistoryIndexService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/claude/ClaudeHistoryIndexService.java)
- [ClaudeHistorySearchService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/claude/ClaudeHistorySearchService.java)
- [ClaudeSessionLiteReader.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/claude/ClaudeSessionLiteReader.java)
- [SessionLiteReader.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/common/SessionLiteReader.java)
- [TagExtractor.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/util/TagExtractor.java)
- [TextSanitizer.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/util/TextSanitizer.java)
- [PathUtils.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/util/PathUtils.java)

这意味着当前项目的 history 不再只是内存会话列表，而是已经有了 provider 侧磁盘读取、解析、lite-read 的基础能力。

### 2. HistoryIndexService 已升级为双层模型

[HistoryIndexService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/history/HistoryIndexService.java) 当前已经从“只读运行期缓存”升级为：

- 运行期 `SessionIndexCache`
- Claude provider 历史读取
- 合并 `favorited/customTitle/entrypoint`
- 统一按 `updatedAt` 输出

也就是说，历史列表的主入口已经开始向参考项目那种“统一索引服务”靠拢，而不是每个 handler 自己直连 provider。

### 3. history handler 链已形成第一轮闭环

当前已有：

- [HistoryLoadService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/history/HistoryLoadService.java)
- [HistoryMessageInjector.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/history/HistoryMessageInjector.java)
- [HistoryExportBridgeService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/history/HistoryExportBridgeService.java)
- [HistoryMetadataBridgeService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/history/HistoryMetadataBridgeService.java)
- [HistorySessionConversionService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/history/HistorySessionConversionService.java)
- [HistoryDeleteService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/history/HistoryDeleteService.java)

当前已经接通的能力包括：

- 历史列表加载
- 历史会话回放
- 历史导出
- 收藏/标题修改
- CLI session 转换
- 批量删除

和旧进度相比，最重要的变化是：

- 当前项目已经不是“history 只有内存索引”
- 也已经不是“缺少 ClaudeHistoryReader / Parser / Search / Index”

### 4. permission 子系统不再是纯占位

当前权限目录已存在：

- [PermissionService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/permission/PermissionService.java)
- [PermissionManager.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/permission/PermissionManager.java)
- [PermissionDecisionStore.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/permission/PermissionDecisionStore.java)
- [PermissionRequestWatcher.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/permission/PermissionRequestWatcher.java)
- [PermissionDialogRouter.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/permission/PermissionDialogRouter.java)
- [PermissionSessionRegistry.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/permission/PermissionSessionRegistry.java)
- [ToolInterceptor.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/permission/ToolInterceptor.java)
- [DiffReviewService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/permission/DiffReviewService.java)

另外 [PermissionHandler.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/handler/PermissionHandler.java) 也已经具备前端对话框请求和响应接线。

判断：

- 当前权限系统仍未达到参考项目的成熟度。
- 但已经不能再描述成“只有空方法的占位实现”。

---

## 与参考项目相比仍明显落后的部分

### 1. Claude history provider 仍是简化版

虽然当前已经补了 `ClaudeHistoryReader/Parser/Index/Search/LiteReader`，但和参考项目相比仍缺：

- 内存缓存策略
- 文件索引持久化
- 增量扫描
- usage 聚合
- 更完整的 deep search
- 更细的异常恢复与回退策略

当前的 [ClaudeHistoryIndexService.java](/Users/janker/Documents/ProteanCopilot/src/main/java/com/protean/copilot/provider/claude/ClaudeHistoryIndexService.java) 本质上还是“lite-read 优先 + parser fallback”的简化实现，不是参考项目那种更完整的索引管理器方案。

### 2. 多 Provider 仍未开始

参考项目已经有完整的：

- `provider/claude/*`
- `provider/codex/*`

当前项目只有 `provider/claude/*` 和少量 `provider/common/*`，没有：

- `CodexSDKBridge`
- Codex history
- Codex usage / session service
- provider 路由层

这决定了当前项目仍然是 Claude-only。

### 3. session 子模块拆分还不够深

参考项目的 session 侧比当前项目更细，典型缺口包括：

- `SessionProviderRouter`
- `SessionMessageOrchestrator`
- `SessionState`
- `CallbackHandler`
- `MessageMerger`
- `ReplayDeduplicator`
- `ClaudeMessageHandler`
- `CodexMessageHandler`

当前项目能跑，但还没有把会话主链沉淀成足够稳定、可扩展的中间层。

### 4. handler 覆盖面仍偏小

当前项目虽然有 `handler/core`、`handler/history`、`handler/diff`、`handler/provider`，但相比参考项目仍明显缺少大量业务 handler，例如：

- settings
- prompt
- skill
- agent
- MCP server
- rewind
- tab/window event
- dependency / node path / project config

所以当前的 handler 体系已经有骨架，但还谈不上“功能面接近参考项目”。

### 5. permission 仍未形成完整产品闭环

虽然权限系统不再是空壳，但当前仍需继续验证和补强：

- 文件写入审批的全链路覆盖
- 命令执行审批接线
- diff review 和权限决策联动
- 持久化决策策略
- 会话隔离与过期策略
- 前后端异常状态恢复

因此更准确的判断是：

- `permission` 已经起步
- 但距离“可放心作为 IDE Agent 安全边界使用”仍有距离

### 6. 技能 / MCP / Prompt 后端闭环仍薄

当前项目已经有 manager 层和前端 UI 痕迹，但和参考项目相比，后端仍缺：

- 更完整的 registry
- provider 注入逻辑
- slash command / skill 管理
- session template / prompt scope
- 更丰富的 handler 接线

这部分目前更像“预留结构”，还不是完成态。

---

## 当前阶段判断

如果把项目分成四层：

1. 插件壳
2. 单 Provider 聊天主链
3. IDE Agent 基础设施
4. 多 Provider + 深权限 + 深历史 + 技能 + 审计的完整产品

当前项目更准确的位置是：

- 第 1 层：已完成
- 第 2 层：已完成
- 第 3 层：进行中
- 第 4 层：刚起步

其中最近最明显的推进，是第 3 层里的：

- history provider 化
- history 统一索引化
- permission 子系统显性成形

---

## 下一阶段建议

### 第一优先级：把 Claude history provider 做深，而不是只做“有”

建议顺序：

1. 给 `ClaudeHistoryIndexService` 增加缓存与索引持久化。
2. 补增量扫描，避免每次全量扫盘。
3. 补真实 `deep_search_history`。
4. 增加 usage / stats 聚合能力。

原因：

- 当前已经有 history 主骨架。
- 继续做深，收益明显高于重新铺一层新 UI。

### 第二优先级：把 permission 真正接到执行闭环

建议顺序：

1. 盘点文件写入、命令执行、diff 应用入口。
2. 把这些入口统一接到 permission 判定。
3. 做前端 dialog 生命周期与超时兜底回归。
4. 补决策记忆策略。

原因：

- 当前 permission 已经有模块，不再需要从零搭目录。
- 下一步重点是“接线闭环”，不是“继续造类型”。

### 第三优先级：继续拆 session 中间层

建议顺序：

1. 抽 provider router。
2. 抽 message orchestrator。
3. 补 replay merge / dedupe。
4. 把当前窗口层和 session 层的粘连继续减薄。

原因：

- 这是未来接 Codex 的前置条件。

### 第四优先级：再决定是否引入 Codex provider

当前更推荐的节奏不是马上大规模抄 `provider/codex/*`，而是：

1. 先让当前 Claude history / permission / session 三层站稳。
2. 再引入 Codex provider，避免刚接进来就被迫二次返工通用层。

---

## 当前结论

`ProteanCopilot` 现在最准确的描述，不再是“参考项目的简化壳子”，而是：

- 已经有参考项目式的主要目录分层
- 已经完成 Claude 主链
- 已经把 history 从内存列表推进到 provider-backed 第一阶段
- 已经把 permission 从占位推进到第一轮可接线子系统

但距离 `jetbrains-cc-gui` 仍然有三条明显差距线：

1. `Codex / 多 Provider` 还没开始。
2. `history / permission / session` 都还没做到参考项目那种完整深度。
3. `skill / MCP / prompt / service` 体系还没有真正闭环。

因此接下来的开发策略应该是：

- 少铺新壳
- 多做现有骨架的纵深闭环
- 优先把 `history + permission + session` 做扎实

