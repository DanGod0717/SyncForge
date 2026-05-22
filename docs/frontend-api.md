# SyncForge 前端调用 API 文档

> 面向前端联调与页面接入，基于当前后端实现整理。
> 
> 最后更新：2026-04-20

## 1. 快速开始

- 后端基地址：`http://localhost:8080`
- WebSocket 地址：`ws://localhost:8080/ws?token=<JWT>`
- Content-Type：`application/json`
- 统一响应结构：

```json
{
  "code": 200,
  "message": "OK",
  "data": {}
}
```

前端建议统一判断：

- `code === 200`：业务成功
- 其他值：业务失败（即使 HTTP 状态码为 200）

---

## 2. 认证与请求头

### 2.1 登录获取 token

- 方法：`POST`
- 路径：`/api/users/login`

请求：

```json
{
  "username": "alice",
  "password": "alice123"
}
```

成功响应：

```json
{
  "code": 200,
  "message": "OK",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "userId": 1,
    "username": "alice"
  }
}
```

### 2.2 需要鉴权的接口

文档与权限相关接口统一携带：

```http
Authorization: Bearer <token>
```

前端建议：登录成功后保存 `token`（如 `localStorage`），HTTP 拦截器自动注入。

---

## 3. 前端常用接口清单

### 3.1 用户

- `POST /api/users` 注册
- `POST /api/users/login` 登录
- `GET /api/users/{id}` 查询用户
- `GET /api/users/by-username/{username}` 按用户名查询

### 3.2 文档

- `POST /api/documents` 创建文档
- `GET /api/documents/{id}` 文档详情
- `PUT /api/documents/{id}/content` 更新内容（带版本）
- `DELETE /api/documents/{id}` 软删除
- `GET /api/documents/mine?page=1&size=20` 我创建的
- `GET /api/documents/shared-with-me?page=1&size=20` 分享给我的
- `GET /api/documents/{id}/latest` 获取最新状态
- `GET /api/documents/{id}/ops?afterVersion=2&limit=200` 追帧

### 3.3 权限

- `PUT /api/documents/{id}/permissions/{targetUserId}` 授权（editor/viewer）
- `DELETE /api/documents/{id}/permissions/{targetUserId}` 撤权（软删除权限记录）

---

## 4. 详细调用示例（可直接用于页面）

## 4.1 注册

`POST /api/users`

```json
{
  "username": "alice",
  "email": "alice@example.com",
  "password": "alice123",
  "status": 1
}
```

失败示例：

```json
{
  "code": 400,
  "message": "username, email and password are required",
  "data": null
}
```

## 4.2 创建文档

`POST /api/documents`

```json
{
  "title": "需求文档",
  "content": "初始内容"
}
```

## 4.3 更新文档内容（版本控制）

`PUT /api/documents/{id}/content`

```json
{
  "content": "更新后的全文内容",
  "version": 3
}
```

冲突示例：

```json
{
  "code": 409,
  "message": "Document version conflict or document not found",
  "data": null
}
```

前端处理建议：

1. 先调 `GET /api/documents/{id}/latest` 拿最新 `version`。
2. 合并用户改动。
3. 重新提交更新。

## 4.4 权限授权

`PUT /api/documents/{id}/permissions/{targetUserId}`

```json
{
  "role": "editor"
}
```

`role` 仅支持：`editor`、`viewer`。

---

## 5. WebSocket / STOMP（实时与协同）

## 5.1 连接与订阅

- 连接：`ws://localhost:8080/ws?token=<JWT>`
- 文档操作广播：`/topic/documents/{id}/ops`
- 个人 ACK：`/user/queue/collab/ack`
- 个人错误：`/user/queue/collab/errors`

建议用户进入文档页时再订阅该文档，离开页面时取消订阅。

## 5.2 客户端发送 OT

发送目标：`/app/documents/{id}/ops`

```json
{
  "clientOpId": "op-20260420-001",
  "baseVersion": 8,
  "opType": "insert",
  "position": 12,
  "content": "A"
}
```

字段说明：

- `clientOpId`：客户端操作唯一 ID（用于幂等）
- `baseVersion`：本地认为的文档版本
- `opType`：`insert` 或 `delete`
- `position`：操作位置（0-based）
- `content`：插入文本（`insert` 必填）
- `deleteLength`：删除长度（`delete` 必填）

## 5.3 服务端返回消息

ACK（仅发给操作发起者）：

```json
{
  "type": "OT_ACK",
  "documentId": 24,
  "clientOpId": "op-20260420-001",
  "serverVersion": 9,
  "accepted": true,
  "message": "OK"
}
```

广播（发给该文档所有订阅者）：

```json
{
  "type": "OT_APPLIED",
  "documentId": 24,
  "serverVersion": 9,
  "authorUserId": 1,
  "clientOpId": "op-20260420-001",
  "opType": "insert",
  "position": 12,
  "content": "A",
  "deleteLength": 0,
  "updatedAt": "2026-04-20T20:00:00"
}
```

错误（发送到 `/user/queue/collab/errors`）：

```json
{
  "code": 400,
  "message": "Forbidden websocket edit",
  "data": null
}
```

---

## 6. 断线恢复（前端实用流程）

推荐默认策略：直接跳最新状态。

1. 重连 WebSocket：`/ws?token=...`
2. 重新订阅：`/topic/documents/{id}/ops`、`/user/queue/collab/ack`、`/user/queue/collab/errors`
3. 调用：`GET /api/documents/{id}/latest`
4. 用返回的 `content + version` 覆盖本地状态

可选追帧策略（需要按操作回放时）：

- `GET /api/documents/{id}/ops?afterVersion=<localVersion>&limit=200`
- 后端返回 `serverVersion > afterVersion` 的操作列表

当返回条数等于 `limit` 时，前端继续循环拉取下一页：

- 把 `afterVersion` 更新为本批最后一条 `serverVersion`
- 再次请求直到返回条数 `< limit`

---

## 7. 错误码与前端动作建议

- `400` 参数错误：提示用户输入问题，不重试
- `401` 未认证/Token 失效：跳转登录并清理本地 token
- `403` 无权限：展示只读或无权限提示
- `404` 资源不存在：提示并返回列表页
- `409` 版本冲突：拉最新版本后重提
- `500` 服务端异常：提示稍后重试并记录日志

常见错误体：

```json
{
  "code": 401,
  "message": "Invalid or expired token",
  "data": null
}
```

---

## 8. 前端封装建议（Vue/TS）

## 8.1 HTTP 封装

- 统一 `baseURL = http://localhost:8080`
- 请求拦截器注入 `Authorization`
- 响应拦截器统一解析 `ApiResponse<T>`
- 业务层只处理 `data`

## 8.2 STOMP 封装

- 连接时携带 JWT（query token）
- 文档页按 `docId` 动态订阅/退订
- 本地维护 `version`
- 发送 OT 时生成唯一 `clientOpId`

`clientOpId` 建议格式：`<userId>-<timestamp>-<seq>`。

---

## 9. 页面调用顺序（建议）

1. 登录页：`POST /api/users/login`，保存 token
2. 列表页：`GET /api/documents/mine` + `GET /api/documents/shared-with-me`
3. 编辑页进入：`GET /api/documents/{id}/latest`
4. 建立 WS 并订阅协同队列
5. 输入触发 OT：发送到 `/app/documents/{id}/ops`
6. 收到 `OT_APPLIED` 后更新本地内容与版本
7. 断线重连：重复步骤 3~6

---

## 10. 参考

- 详细后端接口文档：`docs/api.md`
- 项目启动说明：`README.md`

