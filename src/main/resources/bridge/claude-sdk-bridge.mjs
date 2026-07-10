#!/usr/bin/env node
/**
 * Claude SDK Bridge — 运行在 Node.js 子进程中，充当 Java IntelliJ 插件
 * 与 @anthropic-ai/claude-agent-sdk 之间的适配层。
 *
 * 协议：从 stdin 读取单行 JSON 消息，将单行 JSON 响应写入 stdout。
 * stderr 仅用于日志输出，Java 端会将其记录到日志中。
 *
 * 依赖：
 *   npm install @anthropic-ai/claude-agent-sdk
 *
 * 用法：
 *   node claude-sdk-bridge.mjs
 */

import * as readline from 'node:readline';
import { createRequire } from 'node:module';
import { delimiter, join } from 'node:path';
import { pathToFileURL } from 'node:url';

// ────────────────────────────────────────────────────────────────────────────
// 全局状态
// ────────────────────────────────────────────────────────────────────────────

/** 当前活动的 SDK 会话对象（由 createSession 创建） */
let currentSession = null;

/** 当前会话的 sessionId（由 Java 端分配） */
let currentSessionId = null;

/** 用于中断当前查询的 AbortController */
let currentAbortController = null;

process.stdout.setDefaultEncoding('utf-8');

// ────────────────────────────────────────────────────────────────────────────
// SDK 导入（延迟导入，启动时验证可用性）
// ────────────────────────────────────────────────────────────────────────────

let claudeSDK = null;
let sdkVersion = 'unknown';
let sdkImportError = null;
const SDK_PACKAGE = '@anthropic-ai/claude-agent-sdk';

async function loadClaudeSdk() {
  const failures = [];
  const configuredRoots = (process.env.PROTEAN_CLAUDE_SDK_NODE_MODULES || '')
    .split(delimiter)
    .map((value) => value.trim())
    .filter(Boolean);

  for (const nodeModulesRoot of configuredRoots) {
    try {
      // createRequire anchors package resolution at the managed SDK installation.
      const resolver = createRequire(join(nodeModulesRoot, '.protean-claude-bridge.cjs'));
      const entryPoint = resolver.resolve(SDK_PACKAGE);
      return await import(pathToFileURL(entryPoint).href);
    } catch (error) {
      failures.push(`${nodeModulesRoot}: ${error.message}`);
    }
  }

  try {
    return await import(SDK_PACKAGE);
  } catch (error) {
    failures.push(`default resolution: ${error.message}`);
    throw new Error(failures.join('; '));
  }
}

try {
  claudeSDK = await loadClaudeSdk();
  sdkVersion = claudeSDK.default?.version || claudeSDK.version || 'unknown';
} catch (error) {
  sdkImportError = error.message;
  // SDK 不可用时仍继续运行，向 Java 端报告可诊断的状态。
  console.error(`[bridge] Failed to import ${SDK_PACKAGE}:`, sdkImportError);
}

// ────────────────────────────────────────────────────────────────────────────
// 辅助函数
// ────────────────────────────────────────────────────────────────────────────

/**
 * 向 Java 端发送一条 JSON 消息。
 * 所有输出都写到 stdout，每行一条完整的 JSON。
 */
function respond(message) {
  try {
    process.stdout.write(JSON.stringify(message) + '\n');
  } catch (err) {
    console.error('[bridge] Failed to write response:', err.message);
  }
}

/**
 * 发送错误消息给 Java 端。
 */
function sendError(message, code) {
  respond({ type: 'error', message, code: code || 'BRIDGE_ERROR' });
}

// ────────────────────────────────────────────────────────────────────────────
// 消息路由
// ────────────────────────────────────────────────────────────────────────────

/**
 * 处理来自 Java 端的单条入站消息。
 */
async function handleMessage(msg) {
  switch (msg.type) {
    case 'query':
      return handleQuery(msg);
    case 'resume':
      return handleResume(msg);
    case 'interrupt':
      return handleInterrupt(msg);
    case 'shutdown':
      return handleShutdown();
    case 'prewarm':
      return handlePrewarm(msg);
    default:
      sendError(`Unknown message type: ${msg.type}`, 'UNKNOWN_TYPE');
  }
}

// ────────────────────────────────────────────────────────────────────────────
// SDK 预加载
// ────────────────────────────────────────────────────────────────────────────

/**
 * 处理 prewarm 消息 —— 提前触发 SDK 的 import 和初始化。
 * 这样首次用户查询时 SDK 已加载到内存，消除 3-5 秒的冷启动延迟。
 */
async function handlePrewarm(msg) {
  try {
    // 确保 SDK 已加载（顶层 await 可能已加载，但也可能因错误而未加载）
    if (!claudeSDK) {
      claudeSDK = await loadClaudeSdk();
      sdkVersion = claudeSDK.default?.version || claudeSDK.version || 'unknown';
      sdkImportError = null;
    }

    // 获取查询函数以验证 API 可用
    const queryFn = claudeSDK.query || claudeSDK.default?.query;
    if (typeof queryFn !== 'function') {
      respond({ type: 'prewarmed', status: 'error',
        error: 'SDK does not expose a query() function' });
      return;
    }

    // 创建临时 query 以触发 SDK 内部初始化管线（如认证、hook 注册等），
    // 但不发送实际用户消息
    const queryOptions = {
      prompt: '',
      model: msg.model || 'claude-sonnet-4-6',
      permissionMode: msg.permissionMode || 'bypassPermissions',
      cwd: msg.cwd || process.cwd(),
      maxTurns: 0,
    };

    try {
      const query = queryFn(queryOptions);
      // 立即关闭以释放资源，但 SDK 内部的模块缓存和连接池已初始化
      if (query && typeof query.close === 'function') {
        await query.close();
      }
    } catch (e) {
      // 空 prompt 可能导致 SDK 抛出错误，这不影响预加载效果
      // SDK 模块本身已在 import 时加载完成
    }

    respond({ type: 'prewarmed', status: 'ok' });
  } catch (e) {
    console.error('[bridge] Prewarm failed:', e.message);
    respond({ type: 'prewarmed', status: 'error', error: e.message });
  }
}

// ────────────────────────────────────────────────────────────────────────────
// 查询处理
// ────────────────────────────────────────────────────────────────────────────

/**
 * 处理 query / resume 消息。
 * 如果 SDK 不可用，返回错误。
 * 如果是新会话，先创建会话。
 * 然后发起查询并流式返回结果。
 */
async function handleQuery(msg) {
  const { sessionId, prompt, cwd, model, permissionMode, reasoningEffort } = msg;

  // 如果 SDK 不可用，立即返回错误
  if (!claudeSDK) {
    sendError(
      `Claude Agent SDK (${SDK_PACKAGE}) is not available. ` +
      'Reinstall Claude Code SDK from Settings > SDK dependency management.' +
      (sdkImportError ? ` Import error: ${sdkImportError}` : ''),
      'SDK_NOT_FOUND'
    );
    return;
  }

  // 如果当前有正在运行的查询，先中断
  if (currentAbortController) {
    currentAbortController.abort();
    currentAbortController = null;
  }

  // 创建新的 AbortController 用于此次查询
  currentAbortController = new AbortController();

  try {
    // 如果是新会话（sessionId 变化），创建新的 SDK 会话
    if (!currentSession || currentSessionId !== sessionId) {
      try {
        const SessionClass = claudeSDK.Session || claudeSDK.default?.Session;
        if (SessionClass) {
          currentSession = new SessionClass({ cwd: cwd || process.cwd() });
        } else if (typeof claudeSDK.createSession === 'function') {
          currentSession = claudeSDK.createSession({ cwd: cwd || process.cwd() });
        } else if (typeof claudeSDK.default?.createSession === 'function') {
          currentSession = claudeSDK.default.createSession({ cwd: cwd || process.cwd() });
        } else {
          // 最后兜底：某些 SDK 版本不需要显式创建会话
          currentSession = { cwd: cwd || process.cwd() };
        }
      } catch (e) {
        console.error('[bridge] Failed to create session:', e.message);
        // 不使用 session 对象的兜底方案
        currentSession = { cwd: cwd || process.cwd() };
      }
      currentSessionId = sessionId;
      respond({ type: 'session_created', sessionId, cwd: cwd || process.cwd() });
    }

    // 构建查询选项
    const queryOptions = {
      prompt: prompt,
      model: model || 'claude-sonnet-4-6',
      permissionMode: permissionMode || 'bypassPermissions',
      signal: currentAbortController.signal,
      cwd: cwd || process.cwd(),
    };

    if (reasoningEffort) {
      queryOptions.reasoningEffort = reasoningEffort;
    }

    // 如果有会话对象，传入会话
    if (currentSession && typeof currentSession !== 'object' || Object.keys(currentSession).length > 0) {
      queryOptions.session = currentSession;
    }

    // 获取查询函数 — 兼容多种 SDK 导出方式
    const queryFn = claudeSDK.query || claudeSDK.default?.query;
    if (typeof queryFn !== 'function') {
      sendError('SDK does not expose a query() function. Check SDK version.', 'SDK_API_ERROR');
      return;
    }

    // 发出流开始信号
    respond({ type: 'stream_start', sessionId });

    // 流式处理查询结果
    for await (const event of queryFn(queryOptions)) {
      // 检查是否被中断
      if (currentAbortController && currentAbortController.signal.aborted) {
        break;
      }

      dispatchStreamEvent(event, sessionId);
    }

    // 流结束
    respond({ type: 'stream_end', sessionId });

  } catch (err) {
    if (err.name === 'AbortError') {
      respond({ type: 'stream_end', sessionId, interrupted: true });
    } else {
      console.error('[bridge] Query error:', err);
      sendError(err.message || String(err), err.code || 'QUERY_ERROR');
      // 即使出错也尝试正常结束流
      respond({ type: 'stream_end', sessionId, error: err.message });
    }
  } finally {
    currentAbortController = null;
  }
}

/**
 * 将 SDK 流式事件分发为对应的 JSON 消息。
 */
function dispatchStreamEvent(event, sessionId) {
  switch (event.type) {
    case 'content_block_delta': {
      const delta = event.delta;
      if (!delta) break;
      if (delta.type === 'text_delta') {
        respond({ type: 'content_delta', delta: delta.text });
      } else if (delta.type === 'thinking_delta') {
        respond({ type: 'thinking_delta', delta: delta.thinking });
      } else if (delta.text !== undefined) {
        // 兜底：某些版本的 SDK 可能不嵌套 type
        respond({ type: 'content_delta', delta: delta.text });
      }
      break;
    }

    case 'content_block_start': {
      // 新内容块开始 — 通知前端
      respond({ type: 'block_reset' });
      break;
    }

    case 'tool_use':
      respond({
        type: 'tool_use',
        toolUseId: event.id,
        toolName: event.name,
        toolInput: event.input,
      });
      break;

    case 'tool_result':
      respond({
        type: 'tool_result',
        toolUseId: event.tool_use_id,
        output: event.content,
        isError: event.is_error || false,
      });
      break;

    case 'message_update':
      respond({
        type: 'message_update',
        messages: event.messages,
      });
      break;

    case 'status':
      respond({ type: 'status', text: event.text || event.message || '' });
      break;

    case 'stream_event':
    case 'assistant':
    case 'user':
    case 'result':
      // 某些 SDK 版本的顶层事件中包含完整的消息数据
      if (event.message || event.content) {
        respond({
          type: 'message_update',
          messages: [event],
        });
      }
      break;

    default:
      // 对其他事件类型，尝试提取任何可用的文本/message 内容
      if (event.text) {
        respond({ type: 'content_delta', delta: event.text });
      } else if (event.message) {
        respond({ type: 'content_delta', delta: event.message });
      }
      break;
  }
}

// ────────────────────────────────────────────────────────────────────────────
// 会话恢复
// ────────────────────────────────────────────────────────────────────────────

/**
 * 恢复已存在的会话，发送后续消息。
 * 本质上和 query 相同，但保留 currentSession。
 */
async function handleResume(msg) {
  return handleQuery(msg);
}

// ────────────────────────────────────────────────────────────────────────────
// 中断处理
// ────────────────────────────────────────────────────────────────────────────

/**
 * 中断当前正在运行的查询。
 */
async function handleInterrupt(msg) {
  if (currentAbortController) {
    currentAbortController.abort();
    currentAbortController = null;
    respond({ type: 'status', text: 'Interrupted' });
  } else {
    respond({ type: 'status', text: 'No active query to interrupt' });
  }
}

// ────────────────────────────────────────────────────────────────────────────
// 关闭处理
// ────────────────────────────────────────────────────────────────────────────

/**
 * 优雅关闭桥接进程。
 */
async function handleShutdown() {
  // 先中断当前查询
  if (currentAbortController) {
    currentAbortController.abort();
    currentAbortController = null;
  }
  respond({ type: 'status', text: 'Shutting down' });
  // 给一点时间让消息发送出去
  setTimeout(() => {
    process.exit(0);
  }, 100);
}

// ────────────────────────────────────────────────────────────────────────────
// 主入口：从 stdin 逐行读取 JSON 消息
// ────────────────────────────────────────────────────────────────────────────

const rl = readline.createInterface({
  input: process.stdin,
  crlfDelay: Infinity,
});

// 进程就绪信号
respond({
  type: 'ready',
  version: sdkVersion,
  sdkAvailable: claudeSDK !== null,
  sdkPackage: SDK_PACKAGE,
  importError: sdkImportError,
  hint: claudeSDK === null
    ? 'Reinstall Claude Code SDK from Settings > SDK dependency management.'
    : undefined,
  runtime: `node ${process.version} on ${process.platform}`,
});

rl.on('line', async (line) => {
  const trimmed = line.trim();
  if (!trimmed) return; // 忽略空行

  try {
    const msg = JSON.parse(trimmed);
    await handleMessage(msg);
  } catch (err) {
    console.error('[bridge] Failed to parse message:', err.message);
    sendError('Failed to parse message: ' + err.message, 'PARSE_ERROR');
  }
});

rl.on('close', () => {
  console.error('[bridge] stdin closed, exiting');
  process.exit(0);
});

// 处理 SIGTERM 信号
process.on('SIGTERM', () => {
  console.error('[bridge] Received SIGTERM, shutting down');
  if (currentAbortController) {
    currentAbortController.abort();
  }
  process.exit(0);
});
