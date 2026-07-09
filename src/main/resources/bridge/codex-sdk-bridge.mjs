#!/usr/bin/env node

import * as readline from 'node:readline';

process.stdout.setDefaultEncoding('utf-8');

function respond(message) {
  process.stdout.write(JSON.stringify(message) + '\n');
}

function providerUnavailable(sessionId = null) {
  respond({
    type: 'error',
    code: 'SDK_NOT_FOUND',
    message:
      'Codex provider has been wired on the Java side, but the Node runtime is not bundled in this repository yet. Mirror the Codex ai-bridge implementation from the reference project before enabling live sends.',
    ...(sessionId ? { sessionId } : {}),
  });
  if (sessionId) {
    respond({ type: 'stream_end', sessionId, error: 'Codex runtime unavailable' });
  }
}

async function handleMessage(message) {
  switch (message?.type) {
    case 'prewarm':
      respond({
        type: 'prewarmed',
        status: 'error',
        error:
          'Codex provider bridge process is present, but the actual Codex Node runtime is not bundled yet.',
      });
      break;
    case 'query':
    case 'resume':
      providerUnavailable(message?.sessionId ?? null);
      break;
    case 'interrupt':
      respond({ type: 'stream_end', sessionId: message?.sessionId ?? null, interrupted: true });
      break;
    case 'shutdown':
      process.exit(0);
      break;
    default:
      respond({ type: 'error', code: 'UNKNOWN_TYPE', message: `Unknown message type: ${message?.type}` });
  }
}

respond({ type: 'ready', version: 'stub', sdkAvailable: false });

const rl = readline.createInterface({
  input: process.stdin,
  crlfDelay: Infinity,
});

rl.on('line', async (line) => {
  if (!line || !line.trim()) {
    return;
  }
  try {
    await handleMessage(JSON.parse(line));
  } catch (error) {
    respond({ type: 'error', code: 'BRIDGE_ERROR', message: error?.message ?? String(error) });
  }
});
