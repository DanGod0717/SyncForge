前端 OT 客户端交付规范与实现模板

目标

为前端工程师提供一个完整、可直接使用的 OT（Operational Transformation）客户端实现规范与模板，用以对接现有后端（WebSocket/STOMP + REST + OT 服务端实现）。该文档包含：数据结构、核心类 `OTManager`（TypeScript）、transform 实现、浏览器测试脚本、接口说明、调试与验收用例。

注意

- 前端的 `transform` 语义必须与后端 `TextOperationTransformer` 一致（本文件提供与后端等价的 JS/TS 版本，请保持同步）。
- 字符索引在 JS/Java 中均基于 UTF-16 code units（默认），如果需要 grapheme-aware（emoji 等）请额外引入库并统一前后端度量。

目录

1. 必要交付物
2. 接口与消息格式（精确规范）
3. 前端数据结构
4. `OTManager.ts` 完整实现（可直接复制）
5. 浏览器测试脚本（Console 粘贴即用）
6. 调试/排错清单
7. 验收测试用例
8. 开发建议与注意事项


1) 必要交付物（给前端工程师）

- 本文件 `docs/FRONTEND_OT_SPEC.md`（实现说明与代码模板）
- 后端 API 文档（`docs/api.md`）—— 包含登录、文档、ops、snapshots、reconstruct 的示例
- 测试账号或创建测试用户的脚本（便于快速拿 token）
- 后端 transform 的最终版本说明（若变更需同步）


2) 接口与消息格式（精确规范）

REST

- POST /api/users/login
  - body: { "username": "...", "password": "..." }
  - response.data.token: JWT

- GET /api/documents/{id}/latest
  - response.data -> Document { id, content, version, ... }

- GET /api/documents/{id}/ops?afterVersion={v}&limit={n}
  - 返回按 serverVersion 升序的 DocumentOperation 列表

- GET /api/documents/{id}/snapshots/latest?version={v}
  - 返回 DocumentSnapshot 或 404

- GET /api/documents/{id}/reconstruct?version={v}
  - 返回完整 content（字符串）

WebSocket / STOMP

- Connect: ws://<host>/ws?token=<JWT>
- SUBSCRIBE /topic/documents/{docId}/ops  // 广播事件
- SUBSCRIBE /user/queue/collab/ack         // ACK 给操作人
- SUBSCRIBE /user/queue/collab/errors      // 错误
- SEND destination: /app/documents/{docId}/ops  body: OtEditMessage

消息格式（OtEditMessage）

{
  "clientOpId": "op-xxxx",
  "baseVersion": 12,
  "opType": "insert" | "delete",
  "position": 5,
  "content": "...",       // for insert
  "deleteLength": 3        // for delete
}

Server -> Client (ACK)

{
  "type": "OT_ACK",
  "documentId": 24,
  "clientOpId": "op-xxx",
  "serverVersion": 13,
  "accepted": true
}

Server -> Client (Broadcast)

{
  "type": "OT_APPLIED",
  "documentId": 24,
  "serverVersion": 13,
  "authorUserId": 9,
  "clientOpId": "op-xxx",
  "opType": "insert" | "delete",
  "position": 5,
  "content": "...",
  "deleteLength": 0,
  "updatedAt": "..."
}

Server -> Client (Errors via /user/queue/collab/errors)

{ "code":400, "message":"...", "data":null }


3) 前端数据结构

TypeScript 接口：

```ts
interface OtEditMessage {
  clientOpId: string;
  baseVersion: number;
  opType: 'insert' | 'delete';
  position: number;
  content?: string;
  deleteLength?: number;
}

interface PendingOp {
  clientOpId: string;
  op: OtEditMessage; // copy
  sentAt?: number;
  retries?: number;
}
```

运行态核心状态：

- `docContent: string` - 本地显示文本
- `localVersion: number` - 本地已知 serverVersion
- `pendingOps: PendingOp[]` - 按发送顺序保存的本地未被 ack 的操作


4) `OTManager.ts` 完整实现（可直接复制到前端项目）

说明：该实现使用原生 WebSocket + 简单 STOMP 文本帧方式（轻量），生产建议使用 `@stomp/stompjs`。

```ts
// OTManager.ts
// Minimal OT client manager. Replace `SimpleStompClient` with your stompjs wrapper in production.

type OpType = 'insert' | 'delete';

interface OtEditMessage {
  clientOpId: string;
  baseVersion: number;
  opType: OpType;
  position: number;
  content?: string;
  deleteLength?: number;
}

interface PendingOp {
  clientOpId: string;
  op: OtEditMessage;
  sentAt?: number;
  retries?: number;
}

export class OTManager {
  private wsUrl: string;
  private apiBase: string;
  private docId!: number;
  private token!: string;

  // STOMP client abstraction (you can replace with stompjs)
  private stompSend: (dest: string, body: string) => void = () => {};
  private stompSubscribe: (dest: string, cb: (msg: any) => void) => void = () => {};

  // core state
  public docContent: string = '';
  public localVersion: number = 0;
  public pendingOps: PendingOp[] = [];

  // callbacks
  public onContentChanged: ((content: string, version: number) => void) | null = null;
  public onAck: ((ack: any) => void) | null = null;
  public onError: ((err: any) => void) | null = null;

  constructor(opts: { wsUrl: string; apiBase: string }) {
    this.wsUrl = opts.wsUrl;
    this.apiBase = opts.apiBase;
  }

  // --------- connection helpers (simplified) ---------
  async connect(token: string) {
    this.token = token;
    // TODO: Replace with real stomp client that supports reconnect/backoff.
    // use WebSocket + send CONNECT frame + subscribe
    const ws = new WebSocket(`${this.wsUrl}?token=${encodeURIComponent(token)}`);
    ws.onopen = () => {
      // STOMP CONNECT frame
      ws.send('CONNECT\naccept-version:1.2\nheart-beat:10000,10000\n\n\0');
    };
    ws.onmessage = (e) => {
      const raw = e.data as string;
      const frame = parseStompFrame(raw);
      if (frame.command === 'CONNECTED') {
        // expose stompSend and stompSubscribe using this ws
        this.stompSend = (dest: string, body: string) => {
          const b = body || '';
          ws.send(`SEND\ndestination:${dest}\ncontent-type:application/json\ncontent-length:${b.length}\n\n${b}\0`);
        };
        this.stompSubscribe = (dest: string, cb: (msg: any) => void) => {
          // simple subscription id
          ws.send(`SUBSCRIBE\nid:${Math.random().toString(36).slice(2)}\ndestination:${dest}\n\n\0`);
          // use global onmessage handler to dispatch
        };
      }

      // dispatch to subscription handlers by header
      if (frame.command === 'MESSAGE') {
        const dest = frame.headers.destination || '';
        // basic dispatch
        if (dest.includes('/ops')) this.onRemoteOp(JSON.parse(frame.body));
        else if (dest.includes('/collab/ack')) this.onAck(JSON.parse(frame.body));
        else if (dest.includes('/collab/errors')) this.onError && this.onError(JSON.parse(frame.body));
      }

      if (frame.command === 'ERROR') {
        console.error('STOMP ERROR', frame);
      }
    };

    ws.onerror = (err) => console.error('WS err', err);
    ws.onclose = () => console.log('WS closed');
  }

  subscribeTopics(docId: number) {
    this.docId = docId;
    // subscribe via stompSubscribe wrapper (if using stompjs, use client.subscribe)
    this.stompSubscribe(`/topic/documents/${docId}/ops`, (m) => this.onRemoteOp(m));
    this.stompSubscribe(`/user/queue/collab/ack`, (m) => this.onAck(m));
    this.stompSubscribe(`/user/queue/collab/errors`, (m) => this.onError && this.onError(m));
  }

  // --------- local send API ---------
  sendLocalInsert(position: number, content: string) {
    const op: OtEditMessage = {
      clientOpId: this.generateClientOpId(),
      baseVersion: this.localVersion,
      opType: 'insert',
      position,
      content,
    };
    this.enqueueAndSend(op);
    this.applyOpToLocalDoc(op);
    this.emitContentChanged();
  }

  sendLocalDelete(position: number, deleteLength: number) {
    const op: OtEditMessage = {
      clientOpId: this.generateClientOpId(),
      baseVersion: this.localVersion,
      opType: 'delete',
      position,
      deleteLength,
    };
    this.enqueueAndSend(op);
    this.applyOpToLocalDoc(op);
    this.emitContentChanged();
  }

  enqueueAndSend(op: OtEditMessage) {
    const p: PendingOp = { clientOpId: op.clientOpId, op: JSON.parse(JSON.stringify(op)), sentAt: Date.now(), retries: 0 };
    this.pendingOps.push(p);
    this.sendOpToServer(op);
    // start ACK timeout logic externally if desired
  }

  sendOpToServer(op: OtEditMessage) {
    const dest = `/app/documents/${this.docId}/ops`;
    const b = JSON.stringify(op);
    this.stompSend(dest, b);
  }

  // --------- core handlers (OT) ---------
  onRemoteOp(remoteEvent: any) {
    // remoteEvent is server OT_APPLIED event
    const serverVersion = remoteEvent.serverVersion as number;
    if (serverVersion <= this.localVersion) {
      return; // duplicate
    }

    if (this.pendingOps.length === 0) {
      if (serverVersion === this.localVersion + 1) {
        this.applyOpToLocalDoc(remoteEvent);
        this.localVersion = serverVersion;
        this.emitContentChanged();
      } else {
        // gap: fetch missing ops
        this.fetchMissingOpsAndApply(this.localVersion);
      }
      return;
    }

    // has pendingOps: transform remote against pendingOps
    let transformedRemote = this.copyEventToOt(remoteEvent);
    for (const p of this.pendingOps) {
      transformedRemote = this.transform(transformedRemote, p.op);
    }
    // apply transformed remote
    this.applyOpToLocalDoc(transformedRemote);
    this.localVersion = serverVersion;
    this.emitContentChanged();

    // transform pending ops against original remoteEvent
    for (let i = 0; i < this.pendingOps.length; i++) {
      this.pendingOps[i].op = this.transform(this.pendingOps[i].op, remoteEvent);
    }
  }

  onAck(ackMsg: any) {
    const clientOpId = ackMsg.clientOpId;
    const serverVersion = ackMsg.serverVersion as number;
    const idx = this.pendingOps.findIndex(p => p.clientOpId === clientOpId);
    if (idx >= 0) this.pendingOps.splice(idx, 1);
    this.localVersion = serverVersion;
    this.onAck && this.onAck(ackMsg);
  }

  // --------- apply helper ---------
  applyOpToLocalDoc(op: any) {
    const type = op.opType as OpType;
    const pos = Math.max(0, Math.min(op.position || 0, this.docContent.length));
    if (type === 'insert') {
      const text = op.content || '';
      this.docContent = this.docContent.slice(0, pos) + text + this.docContent.slice(pos);
    } else if (type === 'delete') {
      const del = op.deleteLength || 0;
      const end = Math.max(pos, Math.min(pos + del, this.docContent.length));
      this.docContent = this.docContent.slice(0, pos) + this.docContent.slice(end);
    }
  }

  // --------- transform (must match backend) ---------
  transform(A: OtEditMessage, B: OtEditMessage | any): OtEditMessage {
    if (!A || !B) return JSON.parse(JSON.stringify(A));
    const copy = JSON.parse(JSON.stringify(A));
    if (copy.opType === 'insert') return this.transformInsert(copy, B);
    return this.transformDelete(copy, B);
  }

  transformInsert(incoming: OtEditMessage, history: any): OtEditMessage {
    let position = incoming.position;
    const historyPos = history.position ?? 0;
    if (history.opType === 'insert') {
      const historyLen = (history.content || '').length;
      if (historyLen > 0 && historyPos <= position) position += historyLen;
      incoming.position = position;
      return incoming;
    }
    const hDel = history.deleteLength || 0;
    if (hDel <= 0) return incoming;
    if (historyPos < position) {
      const moved = Math.min(hDel, position - historyPos);
      position -= moved;
      incoming.position = position;
    }
    return incoming;
  }

  transformDelete(incoming: OtEditMessage, history: any): OtEditMessage {
    let start = incoming.position;
    let end = start + (incoming.deleteLength || 0);
    const hPos = history.position ?? 0;

    if (history.opType === 'insert') {
      const insertedLength = (history.content || '').length;
      if (insertedLength <= 0) return incoming;
      if (hPos <= start) {
        start += insertedLength;
        end += insertedLength;
      } else if (hPos < end) {
        end += insertedLength;
      }
    } else {
      const hDel = history.deleteLength || 0;
      if (hDel <= 0) return incoming;
      const hEnd = hPos + hDel;
      if (hEnd <= start) {
        start -= hDel;
        end -= hDel;
      } else if (hPos >= end) {
        // no-op
      } else {
        const overlapStart = Math.max(start, hPos);
        const overlapEnd = Math.min(end, hEnd);
        const overlapLength = Math.max(0, overlapEnd - overlapStart);
        const leftShift = Math.min(Math.max(start - hPos, 0), hDel);
        start -= leftShift;
        end -= (overlapLength + leftShift);
      }
    }

    incoming.position = Math.max(0, start);
    incoming.deleteLength = Math.max(0, end - start);
    return incoming;
  }

  // --------- fetch missing ops (追帧) ---------
  async fetchMissingOpsAndApply(afterVersion: number) {
    const limit = 200;
    const url = `${this.apiBase}/api/documents/${this.docId}/ops?afterVersion=${afterVersion}&limit=${limit}`;
    const res = await fetch(url, { headers: { Authorization: `Bearer ${this.token}` } });
    if (!res.ok) return;
    const json = await res.json();
    const ops = json.data || [];
    for (const ev of ops) this.onRemoteOp(ev);
  }

  // --------- util ---------
  generateClientOpId() {
    return `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
  }

  copyEventToOt(event: any): OtEditMessage {
    return {
      clientOpId: event.clientOpId || '',
      baseVersion: event.baseVersion || 0,
      opType: event.opType,
      position: event.position,
      content: event.content,
      deleteLength: event.deleteLength,
    };
  }

  emitContentChanged() {
    this.onContentChanged && this.onContentChanged(this.docContent, this.localVersion);
  }
}

// small STOMP frame parser (used by simplified websocket implementation above)
function parseStompFrame(raw: string) {
  const text = raw.replace(/\u0000$/, '');
  const sep = text.indexOf('\n\n');
  const headerPart = sep >= 0 ? text.slice(0, sep) : text;
  const bodyPart = sep >= 0 ? text.slice(sep + 2) : '';
  const lines = headerPart.split('\n');
  const command = lines[0] || '';
  const headers: any = {};
  for (let i = 1; i < lines.length; i++) {
    const idx = lines[i].indexOf(':');
    if (idx > 0) {
      const k = lines[i].slice(0, idx).trim();
      const v = lines[i].slice(idx + 1).trim();
      headers[k] = v;
    }
  }
  return { command, headers, body: bodyPart };
}
```

说明：上面代码为模板，建议工程化时替换为 stompjs client（支持 reconnect、subscription 管理、心跳等）。


5) 浏览器测试脚本（Console 粘贴，简化版）

说明：将以下脚本粘贴到浏览器控制台（在可以访问后端的页面）并修改 `BASE_URL`、`DOC_ID`、`TOKEN_A`、`TOKEN_B`。

```js
(async () => {
  const BASE_WS = 'ws://localhost:8080/ws';
  const BASE_API = 'http://localhost:8080';
  const DOC_ID = 24; // 修改
  const TOKEN_A = '<TOKEN_A>'; // 修改
  const TOKEN_B = '<TOKEN_B>'; // 修改

  function parseStomp(raw) {
    const text = raw.replace(/\u0000$/, '');
    const split = text.indexOf('\n\n');
    const head = split >= 0 ? text.slice(0, split) : text;
    const body = split >= 0 ? text.slice(split + 2) : '';
    const lines = head.split('\n');
    const command = lines[0] || '';
    const headers = {};
    for (let i = 1; i < lines.length; i++) {
      const j = lines[i].indexOf(':');
      if (j > 0) headers[lines[i].slice(0, j)] = lines[i].slice(j + 1);
    }
    return { command, headers, body };
  }

  function createClient(name, token) {
    const ws = new WebSocket(`${BASE_WS}?token=${encodeURIComponent(token)}`);
    const state = { connected: false, logs: [] };
    ws.onopen = () => ws.send('CONNECT\naccept-version:1.2\nheart-beat:10000,10000\n\n\0');
    ws.onmessage = (e) => {
      const f = parseStomp(e.data);
      if (f.command === 'CONNECTED') {
        state.connected = true;
        ws.send(`SUBSCRIBE\nid:${name}-ops\ndestination:/topic/documents/${DOC_ID}/ops\n\n\0`);
        ws.send(`SUBSCRIBE\nid:${name}-ack\ndestination:/user/queue/collab/ack\n\n\0`);
        ws.send(`SUBSCRIBE\nid:${name}-err\ndestination:/user/queue/collab/errors\n\n\0`);
        console.log(`[${name}] connected`);
        return;
      }
      if (f.command === 'MESSAGE') {
        let payload = f.body;
        try { payload = JSON.parse(f.body); } catch(e) {}
        const dest = f.headers.destination || '';
        console.log(`[${name}] ${dest}`, payload);
        state.logs.push({ dest, payload });
      }
    };
    ws.onerror = (e) => console.error(`[${name}] ws err`, e);
    ws.onclose = () => console.log(`[${name}] closed`);

    function sendOp(op) {
      if (!state.connected) return console.warn('not connected');
      const body = JSON.stringify(op);
      ws.send(`SEND\ndestination:/app/documents/${DOC_ID}/ops\ncontent-type:application/json\ncontent-length:${body.length}\n\n${body}\0`);
      console.log(`[${name}] SEND`, op);
    }

    return { ws, sendOp, state };
  }

  const A = createClient('A', TOKEN_A);
  const B = createClient('B', TOKEN_B);
  await new Promise(r => setTimeout(r, 1500));

  // case: concurrent insert at position 0
  A.sendOp({ clientOpId: `a-${Date.now()}`, baseVersion: 4, opType: 'insert', position: 0, content: 'A' });
  B.sendOp({ clientOpId: `b-${Date.now()}`, baseVersion: 4, opType: 'insert', position: 0, content: 'B' });

  // observe logs in console for ack/applied/errors
  window.__ot_debug = { A, B };
  console.log('clients created: window.__ot_debug');
})();
```


6) 调试/排错清单

- 如果删除失败检查 `/user/queue/collab/errors` 是否返回 `position out of range` 或 `delete range out of content`。
- 打印并核对：发送时的 `baseVersion`、`position`、`pendingOps` 列表；收到的 `OT_APPLIED` 的 `serverVersion` 和 `position`。
- 确认收到 ACK 后是否按 `clientOpId` 删除对应 pending；确认本地 `localVersion` 已更新。
- 在并发场景，打印每次 transform 的输入输出，确保 transform 方向一致（客户端对 remote 作 transform，服务端对 incoming 作 transform）。


7) 验收测试用例（必须通过）

- 单用户删除到空：初始 "ABCDE" -> 连续 delete 5 次 -> 最终空字符串
- 双用户并发 insert at same pos：A/B 各 insert 'X' 和 'Y' -> 最终两端字符串一致
- 双用户 overlapping deletes（示例）："ABCDEFG"，A 删除 pos=1,len=2，B 删除 pos=2,len=1 -> 最终 "ADEFG"
- 重连后恢复：离线期间产生若干 pendingOps，重连后通过 snapshot+ops 恢复并 transform pending, 最终一致
- clientOpId 重发幂等：同 clientOpId 重发只产生一次 server operation


8) 开发建议与注意事项

- 生产用 stompjs（@stomp/stompjs）或 SockJS + stompjs，避免手写帧实现的边缘问题。
- 为 transform 实现编写丰富的单元测试（jest），与后端 Java 的 `TextOperationTransformerTest` 保持一致的测试向量。
- ACK 超时策略：超时先触发追帧，再决定是否重发；避免在网络抖动时造成重复插入。
- 对 delete 越界在客户端做容错（sanitize position & deleteLength），但仍应让后端做最终校验并报告错误以便排查。
- 考虑把 transform 函数抽成共享库（例如用 JS 实现并在前端/后端都运行同一套算法，后端通过 Graal 或 node 服务复用）以避免语义漂移。


附录：联调快速步骤

1) 启动后端服务并准备两个测试用户 A,B（或使用现有账号）。
2) 登录拿两个 token。
3) 在浏览器控制台粘贴上面的测试脚本并替换 token/docId。
4) 观察 `/user/queue/collab/ack` 和 `/topic/documents/{id}/ops` 的消息序列，验证 transform/ack 行为。


结束

把这个文件交给前端工程师即可开始实现与调试。如需我把 `OTManager.ts` 转成具体 `EditorCollab.vue` 示例或替换为 `@stomp/stompjs` 的完整实现，我可以继续生成代码。
