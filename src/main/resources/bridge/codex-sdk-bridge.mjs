#!/usr/bin/env node

import * as readline from 'node:readline';
import { readFile } from 'node:fs/promises';
import { delimiter, join } from 'node:path';
import { pathToFileURL } from 'node:url';

process.stdout.setDefaultEncoding('utf-8');

let sdkModule = null;
let sdkVersion = 'unknown';
let sdkImportError = null;
const sdkPackageName = '@openai/codex-sdk';

const sessions = new Map();
const aliases = new Map();

function respond(message) {
  try {
    process.stdout.write(JSON.stringify(message) + '\n');
  } catch (error) {
    console.error('[codex-bridge] failed to write response:', error?.message || error);
  }
}

function sendError(message, code = 'BRIDGE_ERROR', sessionId = null, options = {}) {
  respond({
    type: 'error',
    message,
    code,
    sessionId,
    phase: options.phase || null,
    hint: options.hint || null,
    details: options.details || null,
    recoverable: Boolean(options.recoverable),
  });
}

function buildRuntimeSummary() {
  return `node ${process.version} on ${process.platform}`;
}

function sendStatus(text, sessionId = null) {
  respond({ type: 'status', text, sessionId });
}

function buildReadyPayload() {
  return {
    type: 'ready',
    version: sdkVersion,
    sdkAvailable: sdkModule !== null,
    sdkPackage: sdkPackageName,
    runtime: buildRuntimeSummary(),
    cwd: process.cwd(),
    nodeVersion: process.version,
    platform: process.platform,
    pid: process.pid,
    importError: sdkImportError,
    hint: sdkModule
      ? null
      : 'Install @openai/codex-sdk from Settings and ensure Node.js >= 18 is used by the IDE runtime.',
  };
}

async function loadCodexSdk() {
  const failures = [];
  const configuredRoots = (process.env.PROTEAN_CODEX_SDK_NODE_MODULES || '')
    .split(delimiter)
    .map((value) => value.trim())
    .filter(Boolean);

  for (const nodeModulesRoot of configuredRoots) {
    try {
      // The bridge runs from a temporary directory, so resolve ESM from the
      // managed SDK installation instead of relying on the process cwd.
      const packageRoot = join(nodeModulesRoot, ...sdkPackageName.split('/'));
      const packageManifest = JSON.parse(await readFile(join(packageRoot, 'package.json'), 'utf-8'));
      const packageExport = packageManifest.exports?.['.'];
      const entryPoint = typeof packageExport === 'object'
        ? packageExport.import
        : packageExport || packageManifest.module || packageManifest.main;
      if (typeof entryPoint !== 'string' || !entryPoint.startsWith('./')) {
        throw new Error(`unsupported ESM entry point in ${packageRoot}/package.json`);
      }
      return await import(pathToFileURL(join(packageRoot, entryPoint)).href);
    } catch (error) {
      failures.push(`${nodeModulesRoot}: ${error.message}`);
    }
  }

  try {
    return await import(sdkPackageName);
  } catch (error) {
    failures.push(`default resolution: ${error.message}`);
    throw new Error(failures.join('; '));
  }
}

async function ensureSdkLoaded() {
  if (sdkModule) {
    return sdkModule;
  }
  try {
    sdkModule = await loadCodexSdk();
    sdkVersion = sdkModule?.version || sdkModule?.default?.version || 'unknown';
    sdkImportError = null;
    return sdkModule;
  } catch (error) {
    sdkImportError = error?.message || String(error);
    throw error;
  }
}

function resolveSdkCtor(module) {
  const ctor = module?.Codex || module?.default || module;
  if (typeof ctor !== 'function') {
    throw new Error('@openai/codex-sdk does not expose a Codex constructor');
  }
  return ctor;
}

function getPrimarySessionId(state) {
  return state.threadId || state.requestSessionId;
}

function rememberState(state, ...ids) {
  for (const id of ids) {
    if (!id) {
      continue;
    }
    aliases.set(id, state.requestSessionId);
  }
  sessions.set(state.requestSessionId, state);
}

function forgetState(state) {
  for (const [alias, owner] of aliases.entries()) {
    if (owner === state.requestSessionId) {
      aliases.delete(alias);
    }
  }
  sessions.delete(state.requestSessionId);
}

function findState(sessionId) {
  if (!sessionId) {
    return null;
  }
  const owner = aliases.get(sessionId) || sessionId;
  return sessions.get(owner) || null;
}

function createState(message) {
  const state = {
    requestSessionId: message.sessionId,
    threadId: null,
    thread: null,
    abortController: null,
    textCache: new Map(),
    thinkingCache: new Map(),
    toolStates: new Map(),
  };
  rememberState(state, message.sessionId);
  return state;
}

function appendNovel(cache, key, nextValue) {
  if (typeof nextValue !== 'string' || nextValue.length === 0) {
    return '';
  }
  const previous = cache.get(key) || '';
  if (nextValue.startsWith(previous)) {
    const delta = nextValue.slice(previous.length);
    cache.set(key, nextValue);
    return delta;
  }
  cache.set(key, nextValue);
  return nextValue;
}

function normalizeThreadId(event, thread) {
  return event?.thread_id
    || event?.threadId
    || event?.thread?.id
    || thread?.id
    || null;
}

function extractText(item) {
  if (!item || typeof item !== 'object') {
    return '';
  }
  if (typeof item.text === 'string') {
    return item.text;
  }
  if (typeof item.content === 'string') {
    return item.content;
  }
  if (Array.isArray(item.content)) {
    return item.content
      .map((part) => {
        if (typeof part === 'string') {
          return part;
        }
        if (typeof part?.text === 'string') {
          return part.text;
        }
        if (typeof part?.content === 'string') {
          return part.content;
        }
        return '';
      })
      .join('');
  }
  return '';
}

function extractThinking(item) {
  if (!item || typeof item !== 'object') {
    return '';
  }
  if (typeof item.thinking === 'string') {
    return item.thinking;
  }
  if (typeof item.reasoning === 'string') {
    return item.reasoning;
  }
  if (Array.isArray(item.thinking)) {
    return item.thinking
      .map((part) => (typeof part === 'string' ? part : part?.text || ''))
      .join('');
  }
  return '';
}

function extractToolInfo(item, event) {
  const id = item?.id || event?.item_id || event?.id || null;
  const name = item?.tool_name || item?.name || item?.command || event?.name || null;
  const input = item?.input || item?.args || item?.command_input || event?.input || {};
  const output = item?.output || event?.output || null;
  const isError = Boolean(item?.is_error || event?.is_error || event?.error);
  return { id, name, input, output, isError };
}

async function handleMessage(message) {
  switch (message?.type) {
    case 'query':
      return handleRun(message, false);
    case 'resume':
      return handleRun(message, true);
    case 'interrupt':
      return handleInterrupt(message);
    case 'prewarm':
      return handlePrewarm();
    case 'shutdown':
      return handleShutdown();
    default:
      sendError(`Unknown message type: ${message?.type || '(missing)'}`, 'UNKNOWN_TYPE');
  }
}

async function handlePrewarm() {
  try {
    await ensureSdkLoaded();
    respond({ type: 'prewarmed', status: 'ok' });
  } catch (error) {
    console.error('[codex-bridge] prewarm failed:', error?.message || error);
    respond({
      type: 'prewarmed',
      status: 'error',
      error: error?.message || String(error),
      hint: 'Verify the installed @openai/codex-sdk version and Node.js runtime before retrying prewarm.',
    });
  }
}

async function buildThread(message, state, resume) {
  const sdk = await ensureSdkLoaded();
  const Codex = resolveSdkCtor(sdk);
  const codex = new Codex({});

  const threadOptions = {
    approvalPolicy: message.approvalPolicy,
    sandboxMode: message.sandboxMode,
    skipGitRepoCheck: message.skipGitRepoCheck !== false,
    model: message.model,
    modelReasoningEffort: message.reasoningEffort,
    maxTurns: 200,
  };

  if (!resume && message.workingDirectory) {
    threadOptions.workingDirectory = message.workingDirectory;
  }

  if (resume) {
    const resumeId = state.threadId || message.sessionId;
    return codex.resumeThread(resumeId, threadOptions);
  }

  return codex.startThread(threadOptions);
}

async function handleRun(message, resume) {
  let state = findState(message.sessionId);
  if (!state) {
    state = createState(message);
  }

  if (state.abortController) {
    state.abortController.abort();
  }
  state.abortController = new AbortController();
  state.textCache.clear();
  state.thinkingCache.clear();
  state.toolStates.clear();

  try {
    sendStatus(resume ? 'Resuming Codex thread' : 'Starting Codex query', getPrimarySessionId(state));
    respond({ type: 'stream_start', sessionId: getPrimarySessionId(state) });

    state.thread = await buildThread(message, state, resume);
    if (state.thread?.id && !state.threadId) {
      state.threadId = state.thread.id;
      rememberState(state, state.requestSessionId, state.threadId);
      respond({
        type: 'session_created',
        sessionId: state.threadId,
        requestSessionId: state.requestSessionId,
        cwd: message.cwd || message.workingDirectory || process.cwd(),
      });
    }

    const runInput = { input: message.prompt || '' };
    const { events } = await state.thread.runStreamed(runInput, {
      signal: state.abortController.signal,
    });

    for await (const event of events) {
      dispatchEvent(event, state, message);
    }

    sendStatus('Codex stream completed', getPrimarySessionId(state));
    respond({ type: 'stream_end', sessionId: getPrimarySessionId(state) });
  } catch (error) {
    if (error?.name === 'AbortError') {
      sendStatus('Codex run interrupted', getPrimarySessionId(state));
      respond({ type: 'stream_end', sessionId: getPrimarySessionId(state), interrupted: true });
      return;
    }

    console.error('[codex-bridge] run failed:', error);
    sendError(error?.message || String(error), error?.code || 'CODEX_RUN_ERROR', getPrimarySessionId(state), {
      phase: resume ? 'resume' : 'query',
      hint: 'Check approval/sandbox settings, provider auth, and the locked Codex SDK version.',
    });
    respond({ type: 'stream_end', sessionId: getPrimarySessionId(state), error: error?.message || String(error) });
  } finally {
    state.abortController = null;
  }
}

function dispatchEvent(event, state, message) {
  const type = event?.type || '';
  const item = event?.item || event?.delta || null;
  const sessionId = getPrimarySessionId(state);

  if (type === 'thread.started') {
    const threadId = normalizeThreadId(event, state.thread);
    if (threadId && threadId !== state.threadId) {
      state.threadId = threadId;
      rememberState(state, state.requestSessionId, state.threadId);
      respond({
        type: 'session_created',
        sessionId: state.threadId,
        requestSessionId: state.requestSessionId,
        cwd: message.cwd || message.workingDirectory || process.cwd(),
      });
    }
    return;
  }

  if (type === 'item.started' && item?.type === 'agent_message') {
    respond({ type: 'block_reset', sessionId });
  }

  if (type === 'item.started' || type === 'item.updated' || type === 'item.completed') {
    const itemId = item?.id || event?.item_id || 'default';
    const textDelta = appendNovel(state.textCache, itemId, extractText(item));
    if (textDelta) {
      respond({ type: 'content_delta', sessionId, delta: textDelta });
    }

    const thinkingDelta = appendNovel(state.thinkingCache, `${itemId}:thinking`, extractThinking(item));
    if (thinkingDelta) {
      respond({ type: 'thinking_delta', sessionId, delta: thinkingDelta });
    }

    if (item?.type === 'command_execution' || item?.type === 'tool_call' || item?.type === 'mcp_tool_call') {
      const tool = extractToolInfo(item, event);
      if (tool.id && !state.toolStates.has(tool.id)) {
        state.toolStates.set(tool.id, tool.name || 'tool');
        respond({
          type: 'tool_use',
          sessionId,
          toolUseId: tool.id,
          toolName: tool.name || 'tool',
          toolInput: tool.input,
        });
      }

      if (type === 'item.completed' && tool.id) {
        respond({
          type: 'tool_result',
          sessionId,
          toolUseId: tool.id,
          output: tool.output,
          isError: tool.isError,
        });
      }
    }
    return;
  }

  if (type === 'turn.failed' || type === 'error') {
    sendError(event?.error?.message || event?.message || 'Codex turn failed', event?.error?.code || 'TURN_FAILED', sessionId, {
      phase: type,
      hint: 'Inspect the session log and retry the turn after fixing the reported sandbox or approval issue.',
      recoverable: true,
    });
  }
}

function handleInterrupt(message) {
  const state = findState(message?.sessionId);
  if (!state?.abortController) {
    sendStatus('No active Codex run to interrupt', message?.sessionId || null);
    return;
  }
  sendStatus('Interrupting Codex run', getPrimarySessionId(state));
  state.abortController.abort();
}

async function handleShutdown() {
  sendStatus('Shutting down Codex bridge');
  for (const state of sessions.values()) {
    if (state.abortController) {
      state.abortController.abort();
    }
    forgetState(state);
  }
  process.exit(0);
}

try {
  await ensureSdkLoaded();
} catch (error) {
  console.error('[codex-bridge] failed to import @openai/codex-sdk:', error?.message || error);
}

respond(buildReadyPayload());

const rl = readline.createInterface({
  input: process.stdin,
  crlfDelay: Infinity,
});

rl.on('line', async (line) => {
  const trimmed = line?.trim();
  if (!trimmed) {
    return;
  }
  try {
    const message = JSON.parse(trimmed);
    await handleMessage(message);
  } catch (error) {
    console.error('[codex-bridge] failed to process message:', error);
    sendError(error?.message || String(error), 'BRIDGE_MESSAGE_ERROR', null, {
      phase: 'message_dispatch',
      hint: 'Validate the bridge payload shape and provider-specific session parameters.',
    });
  }
});

rl.on('close', () => {
  process.exit(0);
});
