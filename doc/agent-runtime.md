# Agent Runtime

> 本文档描述 Protean Copilot 的 Agent 执行流程。当前为基础实现，待后续详细撰写。

---

## 当前链路

```text
用户输入 (React ChatInputBox)
    │
    ▼
window.sendToJava()  ──JBCefJSQuery──►  handleJavaScriptMessage()
    │                                           │
    │                                    MessageDispatcher
    │                                           │
    │                                    ChatWindowDelegate
    │                                           │
    │                                    SdkBridge.getClaudeBridge()
    │                                           │
    ▼                                           ▼
ClaudeSDKBridge.query(sessionId, prompt, cwd, model, ...)
    │
    ▼
BaseSDKBridge 启动/复用 Node.js 子进程
    │
    ▼
JSON-line IPC (stdin/stdout)
    │
    ▼
bridge/claude-sdk-bridge.mjs
    │
    ▼
@anthropic-ai/claude-code SDK
    │
    ├── query(prompt, options)
    │     │
    │     ├── content_delta  ──►  SessionCallbackAdapter.onContentDelta()
    │     ├── thinking_delta ──►  SessionCallbackAdapter.onThinkingDelta()
    │     ├── tool_use       ──►  SessionCallbackAdapter.onToolUse()
    │     ├── tool_result    ──►  SessionCallbackAdapter.onToolResult()
    │     ├── stream_end     ──►  SessionCallbackAdapter.onStreamEnd()
    │     └── error          ──►  SessionCallbackAdapter.showError()
    │
    └── 所有回调 ──► callJavaScript() ──► window.<function>()
                        │
                        ▼
                  React 前端渲染流式内容
```

## 当前能力

| 能力 | 状态 |
|---|---|
| 流式对话（content_delta） | ✅ |
| 思维链展示（thinking_delta） | ✅ |
| 工具调用可视化（tool_use / tool_result） | ✅ |
| 会话中断（interrupt） | ✅ |
| 多模型切换 | ✅ 前端支持，后端半实现 |
| 权限控制 | ⚪ 桩实现 |
| 会话历史恢复 | ⚪ 桩实现 |
| Daemon 持久化 | 🔜 每次请求独立启动子进程 |

## 流式事件类型

`BaseSDKBridge` 分派 13 种事件：

| 事件 | 方向 | 说明 |
|---|---|---|
| `ready` | SDK → Java | Node.js 桥接就绪 |
| `session_created` | SDK → Java | 会话创建完成 |
| `stream_start` | SDK → Java | 流式响应开始 |
| `content_delta` | SDK → Java | AI 回复内容增量 |
| `thinking_delta` | SDK → Java | 思维链内容增量 |
| `block_reset` | SDK → Java | 内容块重置 |
| `tool_use` | SDK → Java | 工具调用开始 |
| `tool_result` | SDK → Java | 工具调用结果 |
| `message_update` | SDK → Java | 完整消息快照 |
| `status` | SDK → Java | 状态信息 |
| `stream_end` | SDK → Java | 流式响应结束 |
| `streaming_heartbeat` | SDK → Java | 心跳（防止看门狗超时） |
| `error` | SDK → Java | 错误信息 |

## 后续扩展

- **Daemon 持久化**: 将 Node.js 子进程改为长期运行，消除每次 5-10s 启动延迟。
- **权限系统**: 在工具调用前拦截，通过权限对话框获取用户确认。
- **多步执行**: Agent 规划 → 分步执行 → 逐步确认。
- **验证闭环**: 运行测试 → 读取失败 → 回传修复 → 再测试。
- **Codex Provider**: 新增 `CodexSDKBridge`，实现多 Provider 切换。

---

## 相关文件

- [BaseSDKBridge.java](../src/main/java/com/protean/copilot/provider/common/BaseSDKBridge.java) — 核心引擎（831 行）
- [ClaudeSDKBridge.java](../src/main/java/com/protean/copilot/provider/claude/ClaudeSDKBridge.java)
- [SessionCallbackAdapter.java](../src/main/java/com/protean/copilot/session/SessionCallbackAdapter.java)
- [claude-sdk-bridge.mjs](../src/main/resources/bridge/claude-sdk-bridge.mjs)
