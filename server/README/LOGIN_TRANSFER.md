# 扫码登录交接

## 目标

已登录设备可以展示一个短时有效的二维码。全新安装的移动端扫描二维码后，经原设备确认，登录到同一 UniRhy 实例的同一账号。

该功能只传递登录授权，不传递账号密码，也不复制原设备持有的 JWT。新设备完成交接后，由服务端为其签发新的 JWT。

## 安全边界

- 二维码是短时登录凭据，有效期固定为 2 分钟。
- 一个登录交接只能被一台新设备认领。认领被拒绝后，必须重新创建登录交接。
- 原设备必须确认认领，新设备才能创建登录令牌。
- 登录交接只能登录创建它的账号，账号 ID 不接受客户端输入。
- 二维码密钥、认领访问令牌和最终 JWT 不以明文落库。
- 服务端只在创建响应中返回一次二维码密钥，只在认领响应中返回一次认领访问令牌。
- 登录令牌只能成功创建一次，并通过数据库事务及行锁保证原子性。
- 所有包含临时凭据或 JWT 的响应必须携带 `Cache-Control: no-store`。
- 临时凭据不得写入访问日志、业务日志或异常信息。
- 生产环境应使用 HTTPS。使用 HTTP 时，二维码密钥和 JWT 均可能被同一网络中的攻击者截获。

二维码被拍摄或转发后，持有者可以抢先认领，但仍然不能在未经原设备确认的情况下登录。原设备应展示认领设备名称、平台和实例地址，供用户确认。

## 二维码载荷

载荷的规范形式是一段紧凑二进制，各呈现介质按自身特点选择编码：二维码用数字模式（最省），深链接与 NFC 用 Base64URL，蓝牙广播直接发原始字节。

```text
flags(1B) | 地址 | port(2B) | secret(10B)
```

| 字段 | 说明 |
| --- | --- |
| `flags` | bit0-2 协议版本（当前为 1），bit3 scheme（0=http，1=https），bit4-5 地址类型 |
| 地址 | 类型 0：IPv4 四字节；类型 1：后缀字典索引 1B + 标签长度 1B + 标签；类型 2：IPv6 十六字节 |
| `port` | 大端无符号 16 位 |
| `secret` | 10 字节安全随机数 |

地址类型 1 用一张追加式的域名后缀字典（`.com`、`.duckdns.org`、`.tailscale.net` 等）把常见后缀压成一个索引，索引 `0` 表示未命中字典、标签即完整主机名。字典只允许在末尾追加，不得重排或删除；扫描端遇到未知索引应提示升级客户端。

交接 UUID 不进入载荷：`secret` 的摘要在 `qr_secret_hash` 上有唯一索引，同时充当查询键与凭据。

二维码不得包含邮箱、密码、账号 ID、当前设备 JWT 或管理员标记。

载荷由已登录客户端根据服务端创建响应和当前有效服务端 URL 组装。若当前地址为回环地址（`localhost`、`127.x.x.x`、`0.0.0.0`、`[::1]`），客户端应阻止生成二维码，并提示用户配置手机可访问的局域网或公网地址。

新设备扫描后应先展示目标实例的协议、主机和端口。用户继续后，客户端保存该实例 URL，并向该实例认领登录交接。

## 状态模型

登录交接使用单一状态机：

```text
WAITING ──> CLAIMED ──> AUTHORIZED ──> COMPLETED
                └────> REJECTED

WAITING / CLAIMED / AUTHORIZED ──> CANCELLED
WAITING / CLAIMED / AUTHORIZED ──> EXPIRED
```

| 状态 | 说明 |
| --- | --- |
| `WAITING` | 二维码已经创建，等待新设备扫描 |
| `CLAIMED` | 新设备已经认领，等待原设备确认 |
| `AUTHORIZED` | 原设备已经允许登录，等待新设备创建 JWT |
| `COMPLETED` | 新设备 JWT 已经签发，交接完成且不可再次使用 |
| `REJECTED` | 原设备拒绝认领 |
| `CANCELLED` | 原设备主动取消交接 |
| `EXPIRED` | 交接超过有效期 |

`AUTHORIZED` 和 `COMPLETED` 必须分开：批准操作发生在原设备，而 JWT 只能返回给持有认领访问令牌的新设备。原设备批准后，交接进入 `AUTHORIZED`；新设备随后创建 JWT，服务端才将其原子更新为 `COMPLETED`。

`WAITING`、`CLAIMED` 和 `AUTHORIZED` 是活动状态；其余状态是终态。终态之间不能再次转换。

## 数据库变更

数据库迁移文件：

```text
server/src/main/resources/db/migration/V2__create_login_transfer.sql
```

登录交接和认领信息保存在同一张表中；一个登录交接只允许一台新设备认领，不创建独立认领表。

### `login_transfer`

```sql
CREATE TABLE login_transfer
(
    id                      UUID        PRIMARY KEY,
    account_id              BIGINT      NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    qr_secret_hash   BYTEA       NOT NULL,
    claim_token_hash BYTEA,
    device_name      TEXT,
    platform         VARCHAR,
    client_version   TEXT,
    status           VARCHAR     NOT NULL DEFAULT 'WAITING',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at       TIMESTAMPTZ NOT NULL,
    claimed_at       TIMESTAMPTZ,
    authorized_at    TIMESTAMPTZ,
    closed_at        TIMESTAMPTZ,

    CHECK (
        status IN (
            'WAITING',
            'CLAIMED',
            'AUTHORIZED',
            'COMPLETED',
            'REJECTED',
            'CANCELLED',
            'EXPIRED'
        )
    ),
    CHECK (expires_at > created_at)
);

CREATE UNIQUE INDEX login_transfer_account_active_uniq
    ON login_transfer (account_id)
    WHERE status IN ('WAITING', 'CLAIMED', 'AUTHORIZED');

CREATE INDEX login_transfer_expiry_idx
    ON login_transfer (expires_at)
    WHERE status IN ('WAITING', 'CLAIMED', 'AUTHORIZED');
```

字段说明：

- `qr_secret_hash` 保存二维码密钥的 SHA-256 摘要。
- `claim_token_hash` 保存新设备认领访问令牌的 SHA-256 摘要。
- 两种临时凭据都由 32 字节密码学安全随机数生成，数据库不保存可逆密文。
- `claimed_at` 记录新设备成功认领的时间。
- `authorized_at` 记录原设备允许登录的时间；到达过 `AUTHORIZED` 后即保留，因此也可能存在于随后进入的 `CANCELLED` 或 `EXPIRED` 状态。
- `closed_at` 记录进入任意终态的时间。
- 终态记录可以保留认领设备信息，便于状态响应和短期问题排查，但不得保留明文临时凭据或 JWT。

数据库只保证账号引用、状态取值、基本时间范围及每个账号最多一个活动交接。状态转换、认领字段完整性、设备信息校验、摘要长度和时间字段更新由 Service 在事务中负责，避免把业务状态机重复编码为复杂的数据库约束。

### 过期数据清理

- 读取或修改活动交接时，如果 `expires_at <= NOW()`，服务应先将其更新为 `EXPIRED` 并设置 `closed_at`。
- 后台清理任务定期删除进入终态超过保留期的交接记录，运行在独立的单线程 scheduler 上，不与任务分发共用线程。
- 清理只用于控制数据量，接口安全性不能依赖清理任务是否及时执行。

### 配置项

| 配置键 | 环境变量 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `unirhy.login-transfer.ttl-seconds` | `UNIRHY_LOGIN_TRANSFER_TTL_SECONDS` | `120` | 二维码有效期 |
| `unirhy.login-transfer.terminal-retention-hours` | `UNIRHY_LOGIN_TRANSFER_TERMINAL_RETENTION_HOURS` | `24` | 终态记录保留时长 |
| `unirhy.login-transfer.cleanup-interval-ms` | `UNIRHY_LOGIN_TRANSFER_CLEANUP_INTERVAL_MS` | `60000` | 清理任务执行间隔 |

## RESTful API

API 根路径为 `/api/login-transfers`。所有时间均使用 ISO 8601 UTC 时间字符串。

登录交接有两种授权方式：

- 原设备通过 `unirhy-token` 携带账号 JWT，只能操作当前账号创建的交接。
- 新设备通过 `Authorization: Bearer <claim-access-token>` 携带认领访问令牌，只能查询和消费对应交接。

当请求同时携带两种凭据时，服务端应拒绝请求并返回 `400 Bad Request`，避免产生含糊的授权上下文。

### 创建登录交接

```http
POST /api/login-transfers
unirhy-token: <current-jwt>
```

请求体为空。账号 ID 取自当前登录会话。

响应：`201 Created`

```http
Location: /api/login-transfers/018f3cf4-2e9a-7d92-8ae4-5d3f640a69bc
Cache-Control: no-store
```

```json
{
  "id": "018f3cf4-2e9a-7d92-8ae4-5d3f640a69bc",
  "secret": "q6urq6urq6urqw",
  "status": "WAITING",
  "createdAt": "2026-08-02T08:00:00Z",
  "expiresAt": "2026-08-02T08:02:00Z"
}
```

创建新交接时，服务端应在同一事务中锁定账号记录并关闭该账号的旧活动交接：已经到期的更新为 `EXPIRED`，尚未到期的更新为 `CANCELLED`，两者均设置 `closed_at`。随后再插入新交接。数据库的部分唯一索引负责兜底，确保每个账号最多只有一个活动交接。

### 查询登录交接

```http
GET /api/login-transfers/{transferId}
unirhy-token: <current-jwt>
```

原设备只能查询当前账号创建的交接。响应可以包含认领设备信息，但不得包含任何密钥、密钥摘要或 JWT。

响应：`200 OK`

```json
{
  "id": "018f3cf4-2e9a-7d92-8ae4-5d3f640a69bc",
  "status": "CLAIMED",
  "createdAt": "2026-08-02T08:00:00Z",
  "expiresAt": "2026-08-02T08:02:00Z",
  "claimedAt": "2026-08-02T08:00:20Z",
  "authorizedAt": null,
  "closedAt": null,
  "deviceName": "Pixel 9",
  "platform": "ANDROID",
  "clientVersion": "0.1.0"
}
```

尚未认领时，`device` 和 `claimedAt` 为 `null`。

新设备使用同一路径轮询，但必须携带认领访问令牌：

```http
GET /api/login-transfers/{transferId}
Authorization: Bearer <claim-access-token>
```

新设备只获得完成流程所需的最小状态：

```json
{
  "id": "018f3cf4-2e9a-7d92-8ae4-5d3f640a69bc",
  "status": "AUTHORIZED",
  "createdAt": "2026-08-02T08:00:00Z",
  "expiresAt": "2026-08-02T08:02:00Z",
  "authorizedAt": "2026-08-02T08:00:25Z",
  "closedAt": null
}
```

新设备响应不得返回账号或其他设备信息。两侧投影由各自独立的 fetcher 固定，新增的原设备专属字段不会自动对新设备可见。

查询接口不以 `410 Gone` 表达"已过期"：交接进入任何终态（含 `EXPIRED`）后仍返回 `200 OK`，由调用方读取 `status` 判断。到期但仍处于活动态的交接会在本次查询中被落库为 `EXPIRED` 后返回。`404 Not Found` 只用于交接不存在或凭据不匹配。

### 更新登录交接

### 新设备认领

新设备从二维码取得密钥，将交接从 `WAITING` 更新为 `CLAIMED`。该请求不要求账号登录，也不需要交接 id——密钥本身就是查询键。

```http
POST /api/login-transfers/claims
Content-Type: application/json
```

```json
{
  "secret": "q6urq6urq6urqw",
  "deviceName": "Pixel 9",
  "platform": "ANDROID",
  "clientVersion": "0.1.0"
}
```

响应：`201 Created`

```http
Cache-Control: no-store
```

```json
{
  "transfer": {
    "id": "018f3cf4-2e9a-7d92-8ae4-5d3f640a69bc",
    "status": "CLAIMED",
    "createdAt": "2026-08-02T08:00:00Z",
    "expiresAt": "2026-08-02T08:02:00Z",
    "closedAt": null
  },
  "claimAccessToken": "mRBq9BwQByUvtCSmH-_QKXpQQXLsYFeMJlWFNGAaXu0"
}
```

服务端必须在事务中按密钥摘要锁定登录交接，验证其存在、状态为 `WAITING` 且尚未过期，再保存设备信息和认领访问令牌摘要。

二维码密钥错误与交接不存在统一返回 `404 Not Found`，避免枚举有效交接。已经被认领或进入其他非过期状态返回 `409 Conflict`，已过期返回 `410 Gone`。

### 原设备允许或拒绝

原设备使用账号 JWT，将交接从 `CLAIMED` 更新为 `AUTHORIZED` 或 `REJECTED`：

```http
PATCH /api/login-transfers/{transferId}
unirhy-token: <current-jwt>
Content-Type: application/json
```

该接口只接受目标状态，不接受任意字段更新。

允许登录：

```json
{
  "status": "AUTHORIZED"
}
```

拒绝登录：

```json
{
  "status": "REJECTED"
}
```

响应：`200 OK`

```json
{
  "id": "018f3cf4-2e9a-7d92-8ae4-5d3f640a69bc",
  "status": "AUTHORIZED",
  "claimedAt": "2026-08-02T08:00:20Z",
  "authorizedAt": "2026-08-02T08:00:25Z",
  "closedAt": null,
  "deviceName": "Pixel 9",
  "platform": "ANDROID",
  "clientVersion": "0.1.0"
}
```

重复审批或状态不允许返回 `409 Conflict`，交接已过期返回 `410 Gone`。

### 取消登录交接

```http
DELETE /api/login-transfers/{transferId}
unirhy-token: <current-jwt>
```

只有创建该交接的账号可以取消。`WAITING`、`CLAIMED` 或 `AUTHORIZED` 会被更新为 `CANCELLED` 并设置 `closed_at`；已经为 `CANCELLED` 时仍返回成功，使删除操作保持幂等。`COMPLETED` 或 `REJECTED` 返回 `409 Conflict`，`EXPIRED` 返回 `410 Gone`。

响应：`204 No Content`

### 创建登录令牌

```http
POST /api/login-transfers/{transferId}/tokens
Authorization: Bearer <claim-access-token>
```

请求体为空。该接口不要求账号登录，通过认领访问令牌授权。

响应：`201 Created`

```http
Location: /api/tokens/current
Cache-Control: no-store
```

```json
{
  "token": "<new-device-jwt>"
}
```

服务端在一个数据库事务中：

1. 锁定登录交接记录；
2. 验证状态为 `AUTHORIZED` 且未过期；
3. 验证认领访问令牌摘要；
4. 根据 `account_id` 加载账号及管理员标记；
5. 为该账号签发新的 JWT；
6. 将状态更新为 `COMPLETED` 并设置 `closed_at`；
7. 提交事务并返回 JWT。

并发请求中只能有一个请求从 `AUTHORIZED` 转移为 `COMPLETED`。其余请求返回 `409 Conflict`，不得再次签发令牌。

如果服务端已经提交事务，但客户端没有收到响应，新设备需要重新扫码。已签发 JWT 不以明文或可逆形式保存，未收到响应的客户端无法恢复该响应。

## 通用错误响应

接口沿用项目统一错误结构。业务错误至少应覆盖：

| HTTP 状态 | 场景 |
| --- | --- |
| `400 Bad Request` | URI、平台、设备名称、目标状态或授权上下文不合法 |
| `401 Unauthorized` | 当前账号 JWT 或认领访问令牌缺失、无效 |
| `403 Forbidden` | 当前账号不是交接创建者 |
| `404 Not Found` | 资源不存在或二维码密钥错误 |
| `409 Conflict` | 当前状态不允许请求的转换或已经创建 JWT |
| `410 Gone` | 登录交接已过期（仅用于会改变状态的接口，查询接口返回 `200 OK` 与真实终态） |
| `429 Too Many Requests` | 创建或验证请求超过限流阈值 |

## 并发与事务规则

- 创建登录交接时必须锁定对应的 `account` 记录，再关闭旧交接并插入新交接。
- 认领、审批、取消和创建登录令牌时，必须对 `login_transfer` 执行行级锁定。
- 所有状态检查和状态更新必须位于同一事务内，不能采用先查询再无条件更新的方式。
- 临时凭据摘要比较应使用恒定时间比较。
- UUID 及临时凭据均由服务端使用密码学安全随机源生成。
- 服务端时间是过期判断的唯一依据，客户端倒计时仅用于界面展示。

## 限流

至少对以下操作实施限流：

- 每个账号创建登录交接；
- 每个 IP 认领登录交接；
- 每个交接验证错误的二维码密钥；
- 每个交接验证错误的认领访问令牌。

达到阈值返回 `429 Too Many Requests`。限流记录不属于登录交接业务表，可以使用现有基础设施或进程内短时计数器；数据库中的一次性状态仍然是最终判定依据。

## 客户端交互

### 已登录设备

1. 创建登录交接。
2. 使用响应中的 `id`、`secret` 和当前服务端 URL 生成二维码。
3. 每秒查询一次登录交接。
4. 状态进入 `CLAIMED` 后展示设备信息，允许授权或拒绝。
5. 交接完成、拒绝、取消或过期后停止轮询并销毁页面内保存的二维码密钥。
6. 用户关闭二维码弹窗时取消仍处于活动状态的交接。

### 新设备

1. 在登录页选择“扫描二维码登录”。
2. 校验二维码协议版本、实例 URL、UUID 和密钥编码。
3. 展示目标实例地址并将交接更新为 `CLAIMED`。
4. 只在内存中保存认领访问令牌，每秒查询一次交接状态。
5. 状态进入 `AUTHORIZED` 后创建登录令牌。
6. 成功后持久化实例 URL 和新 JWT，清除临时凭据并进入首页。
7. 状态进入 `REJECTED`、`CANCELLED`、`EXPIRED` 或发生不可恢复冲突时，清除临时凭据并返回登录页。

## API 客户端与跨域

- 服务端接口进入 OpenAPI，并通过项目既有生成流程更新前端 API 客户端。
- 不手工修改 `web/src/__generated` 下的文件。
- 使用 `Authorization` 请求头的接口必须确保跨域配置允许该请求头。
- 认领和令牌创建虽然无需账号登录，但仍必须显式执行各自的临时凭据校验。

## 非目标

不包含：

- 账号密码传输；
- 复制原设备 JWT；
- 多个新设备竞争同一个二维码；
- 已签发 JWT 的响应恢复；
- 设备列表、远程下线或按设备撤销 JWT；
- 通过系统相机或通用网页完成扫码；
- 跨实例账号迁移。
