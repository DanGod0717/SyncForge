#!/usr/bin/env node
/*
 * Browser-based OT stress test for SyncForge.
 *
 * What it does:
 * - launches a dedicated Chrome/Edge instance
 * - opens 2 browser targets (Alice/Bob)
 * - logs in through the real HTTP API
 * - connects STOMP over the real WebSocket endpoint
 * - sends 2000 random OT ops total (1000 per user)
 * - waits for ACK / broadcast completion
 * - checks final document version
 *
 * Usage:
 *   node tools/browser-ot-stress.mjs --docId 24 --opsPerClient 1000
 */

import { spawn } from 'node:child_process';
import { mkdtempSync, rmSync, existsSync } from 'node:fs';
import { join } from 'node:path';
import { tmpdir } from 'node:os';

const DEFAULT_BASE_URL = 'http://localhost:8080';
const DEFAULT_WS_URL = 'ws://localhost:8080/ws';
const DEFAULT_DOC_ID = 24;
const DEFAULT_OPS_PER_CLIENT = 1000;
const DEFAULT_USERS = [
  { username: 'zeng', password: '123456' },
  { username: 'lin', password: '123456' },
];

const argv = parseArgs(process.argv.slice(2));
const BASE_URL = argv.baseUrl ?? process.env.BASE_URL ?? DEFAULT_BASE_URL;
const WS_URL = argv.wsUrl ?? process.env.WS_URL ?? DEFAULT_WS_URL;
const DOC_ID = Number(argv.docId ?? process.env.DOC_ID ?? DEFAULT_DOC_ID);
const OPS_PER_CLIENT = Number(argv.opsPerClient ?? process.env.OPS_PER_CLIENT ?? DEFAULT_OPS_PER_CLIENT);
const USER_A = {
  username: argv.userA ?? process.env.USER_A ?? DEFAULT_USERS[0].username,
  password: argv.passA ?? process.env.PASS_A ?? DEFAULT_USERS[0].password,
};
const USER_B = {
  username: argv.userB ?? process.env.USER_B ?? DEFAULT_USERS[1].username,
  password: argv.passB ?? process.env.PASS_B ?? DEFAULT_USERS[1].password,
};

const CHROME_PATH = findChromePath();
if (!CHROME_PATH) {
  throw new Error('Cannot find Chrome/Edge executable. Please set CHROME_PATH manually.');
}

const userDataDir = mkdtempSync(join(tmpdir(), 'syncforge-ot-browser-'));
const remoteDebugPort = await pickPort([9222, 9223, 9224, 9230, 9333]);
const chrome = launchChrome(CHROME_PATH, userDataDir, remoteDebugPort);

let exitCode = 0;
// CDPConnection must be defined before usage
class CDPConnection {
  constructor(wsUrl) {
    this.ws = new WebSocket(wsUrl);
    this.nextId = 1;
    this.pending = new Map();
    this.sessionWaiters = new Map();
    this.ws.addEventListener('message', (ev) => this.onMessage(ev.data));
    this.ws.addEventListener('open', () => {});
  }

  onMessage(data) {
    const msg = JSON.parse(data);
    if (msg.id) {
      const pending = this.pending.get(msg.id);
      if (!pending) return;
      this.pending.delete(msg.id);
      if (msg.error) pending.reject(new Error(JSON.stringify(msg.error)));
      else pending.resolve(msg.result ?? msg);
      return;
    }
    if (msg.method === 'Target.attachedToTarget' && msg.params?.sessionId) {
      const key = msg.params.targetInfo?.targetId;
      const waiter = this.sessionWaiters.get(key);
      if (waiter) {
        this.sessionWaiters.delete(key);
        waiter.resolve(msg.params.sessionId);
      }
    }
  }

  send(method, params = {}, sessionId) {
    const id = this.nextId++;
    const payload = { id, method, params };
    if (sessionId) payload.sessionId = sessionId;
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      this.ws.send(JSON.stringify(payload));
    });
  }
}
try {
  const versionInfo = await waitForDevTools(remoteDebugPort, 20000);
  const cdp = new CDPConnection(versionInfo.webSocketDebuggerUrl);

  const initialLatest = await fetchJson(`${BASE_URL}/api/documents/${DOC_ID}/latest`, {
    headers: { Authorization: `Bearer ${await loginOnce(USER_A)}` },
  });
  if (initialLatest.code !== 200) {
    throw new Error(`Initial latest query failed: ${JSON.stringify(initialLatest)}`);
  }
  const initialVersion = initialLatest.data?.version ?? 0;
  const initialLength = (initialLatest.data?.content ?? '').length;

  console.log(`[INIT] docId=${DOC_ID} version=${initialVersion} contentLength=${initialLength}`);
  console.log(`[INIT] chrome=${CHROME_PATH}`);
  console.log(`[INIT] devtoolsPort=${remoteDebugPort}`);

  const alice = await runBrowserClient(cdp, {
    baseUrl: BASE_URL,
    wsUrl: WS_URL,
    docId: DOC_ID,
    opsPerClient: OPS_PER_CLIENT,
    username: USER_A.username,
    password: USER_A.password,
    label: 'A',
  });

  const bob = await runBrowserClient(cdp, {
    baseUrl: BASE_URL,
    wsUrl: WS_URL,
    docId: DOC_ID,
    opsPerClient: OPS_PER_CLIENT,
    username: USER_B.username,
    password: USER_B.password,
    label: 'B',
  });

  const finalLatest = await fetchJson(`${BASE_URL}/api/documents/${DOC_ID}/latest`, {
    headers: { Authorization: `Bearer ${await loginOnce(USER_A)}` },
  });
  if (finalLatest.code !== 200) {
    throw new Error(`Final latest query failed: ${JSON.stringify(finalLatest)}`);
  }
  const finalVersion = finalLatest.data?.version ?? 0;
  const finalLength = (finalLatest.data?.content ?? '').length;

  const expectedVersion = initialVersion + OPS_PER_CLIENT * 2;
  console.log('\n========== RESULT ==========' );
  console.log(JSON.stringify({
    docId: DOC_ID,
    initialVersion,
    finalVersion,
    expectedVersion,
    initialLength,
    finalLength,
    alice,
    bob,
  }, null, 2));

  if (alice.errors !== 0 || bob.errors !== 0) {
    throw new Error(`Browser client reported errors: A=${alice.errors}, B=${bob.errors}`);
  }
  if (alice.acks !== OPS_PER_CLIENT || bob.acks !== OPS_PER_CLIENT) {
    throw new Error(`ACK count mismatch: A=${alice.acks}, B=${bob.acks}`);
  }
  if (alice.applied !== OPS_PER_CLIENT * 2 || bob.applied !== OPS_PER_CLIENT * 2) {
    throw new Error(`Broadcast count mismatch: A=${alice.applied}, B=${bob.applied}`);
  }
  if (finalVersion !== expectedVersion) {
    throw new Error(`Version mismatch: final=${finalVersion}, expected=${expectedVersion}`);
  }

  console.log('\n[PASS] Browser OT stress test passed.');
} catch (err) {
  exitCode = 1;
  console.error('\n[FAIL]', err?.stack || err);
} finally {
  try {
    chrome.kill('SIGKILL');
  } catch {}
  try {
    rmSync(userDataDir, { recursive: true, force: true });
  } catch {}
  process.exitCode = exitCode;
}

async function runBrowserClient(cdp, cfg) {
  const targetId = await cdp.send('Target.createTarget', { url: 'about:blank' });
  const sessionId = await cdp.send('Target.attachToTarget', { targetId: targetId.targetId, flatten: true });
  await cdp.send('Runtime.enable', {}, sessionId.sessionId);
  await cdp.send('Page.enable', {}, sessionId.sessionId);
  await cdp.send('Console.enable', {}, sessionId.sessionId);
  const expression = `(${browserClientTask.toString()})(${JSON.stringify(cfg)})`;
  const result = await cdp.send('Runtime.evaluate', {
    expression,
    awaitPromise: true,
    returnByValue: true,
    userGesture: true,
  }, sessionId.sessionId);
  if (result.exceptionDetails) {
    throw new Error(`Browser client ${cfg.label} failed: ${JSON.stringify(result.exceptionDetails)}`);
  }
  return result.result.value;
}

async function loginOnce(user) {
  const res = await fetch(`${BASE_URL}/api/users/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify(user),
  });
  const json = await res.json();
  if (json.code !== 200 || !json.data?.token) {
    throw new Error(`login failed for ${user.username}: ${JSON.stringify(json)}`);
  }
  return json.data.token;
}

async function fetchJson(url, init) {
  const res = await fetch(url, init);
  return res.json();
}

function browserClientTask(cfg) {
  return (async () => {
    const state = {
      label: cfg.label,
      username: cfg.username,
      sent: 0,
      acks: 0,
      applied: 0,
      errors: 0,
      content: '',
      version: 0,
      seenAppliedIds: new Set(),
      errorMessages: [],
      connected: false,
    };

    const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
    const randInt = (min, max) => Math.floor(Math.random() * (max - min + 1)) + min;
    const letters = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789';

    const initial = await fetchJson(`${cfg.baseUrl}/api/documents/${cfg.docId}/latest`, {
      headers: { Authorization: `Bearer ${await login(cfg.username, cfg.password)}` },
    });
    if (initial.code !== 200) {
      throw new Error(`[${cfg.label}] initial latest failed: ${JSON.stringify(initial)}`);
    }
    state.version = initial.data?.version ?? 0;
    state.content = initial.data?.content ?? '';

    const token = await login(cfg.username, cfg.password);
    const ws = new WebSocket(`${cfg.wsUrl}?token=${encodeURIComponent(token)}`);

    const connected = new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error(`[${cfg.label}] websocket connect timeout`)), 15000);
      ws.addEventListener('open', () => {
        ws.send(stompFrame('CONNECT', { 'accept-version': '1.2', 'heart-beat': '10000,10000' }));
      });
      ws.addEventListener('message', (ev) => {
        for (const raw of String(ev.data).split('\u0000')) {
          const frame = parseStompFrame(raw);
          if (!frame) continue;
          if (frame.command === 'CONNECTED') {
            state.connected = true;
            clearTimeout(timer);
            ws.send(stompFrame('SUBSCRIBE', { id: `${cfg.label}-ops`, destination: `/topic/documents/${cfg.docId}/ops` }));
            ws.send(stompFrame('SUBSCRIBE', { id: `${cfg.label}-ack`, destination: '/user/queue/collab/ack' }));
            ws.send(stompFrame('SUBSCRIBE', { id: `${cfg.label}-err`, destination: '/user/queue/collab/errors' }));
            resolve(true);
            continue;
          }
          if (frame.command === 'MESSAGE') {
            handleMessage(frame);
          } else if (frame.command === 'ERROR') {
            state.errors++;
            state.errorMessages.push(frame.body?.slice?.(0, 300) ?? String(frame.body));
          }
        }
      });
      ws.addEventListener('error', () => reject(new Error(`[${cfg.label}] websocket error`)));
      ws.addEventListener('close', () => {
        if (!state.connected) reject(new Error(`[${cfg.label}] websocket closed before CONNECTED`));
      });
    });

    await connected;

    for (let i = 0; i < cfg.opsPerClient; i++) {
      if (i > 0 && i % 100 === 0) {
        const sync = await fetchJson(`${cfg.baseUrl}/api/documents/${cfg.docId}/latest`, {
          headers: { Authorization: `Bearer ${token}` },
        });
        if (sync.code === 200) {
          state.version = sync.data?.version ?? state.version;
          state.content = sync.data?.content ?? state.content;
        }
      }

      const op = makeRandomOp(state.content);
      const clientOpId = `${cfg.label}-${i}-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`;
      const payload = {
        clientOpId,
        baseVersion: state.version,
        opType: op.opType,
        position: op.position,
      };
      if (op.opType === 'insert') payload.content = op.content;
      if (op.opType === 'delete') payload.deleteLength = op.deleteLength;

      ws.send(stompFrame('SEND', {
        destination: `/app/documents/${cfg.docId}/ops`,
        'content-type': 'application/json',
      }, JSON.stringify(payload)));

      state.sent++;
      await sleep(randInt(5, 20));
    }

    const deadline = Date.now() + 180000;
    while (Date.now() < deadline) {
      if (state.acks >= cfg.opsPerClient && state.applied >= cfg.opsPerClient * 2) break;
      await sleep(100);
    }

    if (state.acks < cfg.opsPerClient) {
      throw new Error(`[${cfg.label}] ack timeout: ${state.acks}/${cfg.opsPerClient}`);
    }
    if (state.applied < cfg.opsPerClient * 2) {
      throw new Error(`[${cfg.label}] broadcast timeout: ${state.applied}/${cfg.opsPerClient * 2}`);
    }
    if (state.errors !== 0) {
      throw new Error(`[${cfg.label}] got ${state.errors} websocket errors: ${JSON.stringify(state.errorMessages.slice(0, 5))}`);
    }

    const final = await fetchJson(`${cfg.baseUrl}/api/documents/${cfg.docId}/latest`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (final.code !== 200) {
      throw new Error(`[${cfg.label}] final latest failed: ${JSON.stringify(final)}`);
    }

    return {
      label: cfg.label,
      username: cfg.username,
      sent: state.sent,
      acks: state.acks,
      applied: state.applied,
      errors: state.errors,
      initialVersion: initial.data?.version ?? 0,
      finalVersion: final.data?.version ?? 0,
      initialLength: (initial.data?.content ?? '').length,
      finalLength: (final.data?.content ?? '').length,
    };

    function handleMessage(frame) {
      const dest = frame.headers.destination || '';
      let data = frame.body;
      try { data = JSON.parse(frame.body); } catch {}
      if (dest.includes('/ack')) {
        state.acks++;
        if (data?.serverVersion != null) state.version = Math.max(state.version, data.serverVersion);
        return;
      }
      if (dest.includes('/errors')) {
        state.errors++;
        state.errorMessages.push(typeof data === 'string' ? data : JSON.stringify(data));
        return;
      }
      if (dest.includes('/ops')) {
        state.applied++;
        if (data?.serverVersion != null) state.version = Math.max(state.version, data.serverVersion);
        if (data?.clientOpId && state.seenAppliedIds.has(data.clientOpId)) return;
        if (data?.clientOpId) state.seenAppliedIds.add(data.clientOpId);
        if (data?.opType === 'insert') {
          state.content = applyOp(state.content, data);
        } else if (data?.opType === 'delete') {
          state.content = applyOp(state.content, data);
        }
      }
    }

    async function login(username, password) {
      const res = await fetch(`${cfg.baseUrl}/api/users/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify({ username, password }),
      });
      const json = await res.json();
      if (json.code !== 200 || !json.data?.token) {
        throw new Error(`[${cfg.label}] login failed for ${username}: ${JSON.stringify(json)}`);
      }
      return json.data.token;
    }

    async function fetchJson(url, init) {
      const res = await fetch(url, init);
      return res.json();
    }

    function makeRandomOp(content) {
      if (content.length === 0 || Math.random() < 0.6) {
        return {
          opType: 'insert',
          position: randInt(0, content.length),
          content: randomText(),
        };
      }
      const position = randInt(0, content.length - 1);
      const deleteLength = randInt(1, Math.min(3, content.length - position));
      return {
        opType: 'delete',
        position,
        deleteLength,
      };
    }

    function randomText() {
      const len = randInt(1, 3);
      let out = '';
      for (let i = 0; i < len; i++) {
        out += letters[randInt(0, letters.length - 1)];
      }
      return out;
    }

    function applyOp(content, op) {
      if (op.opType === 'insert') {
        const pos = clamp(op.position ?? 0, 0, content.length);
        const ins = String(op.content ?? '');
        return content.slice(0, pos) + ins + content.slice(pos);
      }
      if (op.opType === 'delete') {
        const pos = clamp(op.position ?? 0, 0, content.length);
        const del = Math.max(0, Number(op.deleteLength ?? 0));
        return content.slice(0, pos) + content.slice(Math.min(content.length, pos + del));
      }
      return content;
    }

    function clamp(v, min, max) {
      return Math.min(max, Math.max(min, v));
    }

    function parseStompFrame(raw) {
      const text = String(raw).replace(/\r/g, '');
      if (!text.trim()) return null;
      const sep = text.indexOf('\n\n');
      const head = sep >= 0 ? text.slice(0, sep) : text;
      const body = sep >= 0 ? text.slice(sep + 2).replace(/\u0000/g, '') : '';
      const lines = head.split('\n');
      const command = (lines[0] || '').trim();
      if (!command) return null;
      const headers = {};
      for (let i = 1; i < lines.length; i++) {
        const line = lines[i];
        const idx = line.indexOf(':');
        if (idx > 0) headers[line.slice(0, idx)] = line.slice(idx + 1);
      }
      return { command, headers, body };
    }

    function stompFrame(command, headers = {}, body = '') {
      let out = `${command}\n`;
      for (const [k, v] of Object.entries(headers)) {
        out += `${k}:${v}\n`;
      }
      if (body && !('content-length' in headers)) {
        out += `content-length:${new TextEncoder().encode(body).length}\n`;
      }
      out += `\n${body}\u0000`;
      return out;
    }
  })();
}


async function waitForDevTools(port, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const res = await fetch(`http://127.0.0.1:${port}/json/version`);
      if (res.ok) return await res.json();
    } catch {}
    await sleep(250);
  }
  throw new Error(`DevTools endpoint not ready on port ${port}`);
}

async function pickPort(candidates) {
  for (const port of candidates) {
    if (!(await isPortUsed(port))) return port;
  }
  throw new Error(`No free remote debugging port found among: ${candidates.join(', ')}`);
}

async function isPortUsed(port) {
  try {
    const res = await fetch(`http://127.0.0.1:${port}/json/version`);
    return res.ok;
  } catch {
    return false;
  }
}

function launchChrome(chromePath, userDataDir, port) {
  const args = [
    `--remote-debugging-port=${port}`,
    `--user-data-dir=${userDataDir}`,
    '--no-first-run',
    '--no-default-browser-check',
    '--disable-web-security',
    '--allow-running-insecure-content',
    '--disable-features=IsolateOrigins,site-per-process',
    '--headless=new',
    'about:blank',
  ];
  return spawn(chromePath, args, {
    detached: true,
    stdio: 'ignore',
    windowsHide: true,
  });
}

function findChromePath() {
  const candidates = [
    process.env.CHROME_PATH,
    'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe',
    'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
    'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  ].filter(Boolean);
  return candidates.find((p) => existsSync(p));
}

function parseArgs(args) {
  const out = {};
  for (let i = 0; i < args.length; i++) {
    const arg = args[i];
    if (!arg.startsWith('--')) continue;
    const keyVal = arg.slice(2).split('=');
    if (keyVal.length > 1) {
      out[keyVal[0]] = keyVal.slice(1).join('=');
    } else {
      const key = keyVal[0];
      const next = args[i + 1];
      if (next && !next.startsWith('--')) {
        out[key] = next;
        i++;
      } else {
        out[key] = true;
      }
    }
  }
  return out;
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

