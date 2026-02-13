## Server `api-e2e` 全接口补全实施计划

## 摘要
基于 `/Users/yang/Documents/code/UniRhy/server/api-e2e/src/test/kotlin/com/unirhy/e2e/SmokeTest.kt` 与 `/Users/yang/Documents/code/UniRhy/server/src/main/kotlin/com/coooolfan/unirhy/controller` 的现状，当前约 37 个接口方法里仅覆盖约 7 个。  
目标是：**主流程 + 常见错误**，并且**先按现状契约补测**（不在本轮改接口契约）。

## 分阶段实施（6 阶段）
| 阶段 | 目标 | 接口范围 | 主要产出 | 退出标准 |
|:--|:--|:--|:--|:--|
| 1. 底座与清单 | 先把可扩展测试骨架搭好 | 无新增业务接口 | 扩展 `/Users/yang/Documents/code/UniRhy/server/api-e2e/src/test/kotlin/com/unirhy/e2e/support/E2eHttpClient.kt`（补 `PUT/DELETE`、query/form/json 统一发送）；新增场景状态与断言工具；建立接口覆盖矩阵文件 | 能以统一 DSL 编写任意方法接口测试，覆盖矩阵列出全部 37 接口 |
| 2. 系统与认证 | 完成系统生命周期与会话闭环 | `/api/system/config/*`、`/api/token` | 新增系统/认证 e2e 用例类；覆盖初始化、登录、登出、配置读取更新 | 系统与认证接口主流程全绿，未登录访问受保护接口返回 401，错误登录可稳定断言 |
| 3. 存储配置域 | 完成 FS/OSS 配置 CRUD 与系统配置联动 | `/api/storage/fs/*`、`/api/storage/oss/*`、`PUT /api/system/config` | 新增存储域 e2e；覆盖创建/读取/更新/删除与系统引用关系 | FS/OSS 主流程全绿，常见错误（删除当前系统 FS、设置非法系统存储）可稳定断言 |
| 4. 任务与内容读取 | 完成扫描任务与内容读取主链路 | `/api/task/*`、`/api/work*`、`/api/album*`、`/api/media/{id}` | 新增任务与内容域 e2e；复用扫描样本准备与异步等待 | 扫描-入库-读取链路全绿，常见错误（重复任务冲突、随机参数非法、媒体错误 range）可稳定断言 |
| 5. 账户与播放列表 | 完成用户态写接口与资源归属验证 | `/api/account*`、`/api/playlist*` | 新增账户与播放列表 e2e；引入双账户场景校验所有权 | 账户与播放列表主流程全绿，常见错误（跨用户访问播放列表、目标不存在）可稳定断言 |
| 6. 收敛与执行分层 | 形成可持续运行策略 | 全量 | `smoke/full` 标签分层；整理运行文档与覆盖矩阵；稳定性治理 | CI 仅跑 smoke，full 可本地/夜间稳定跑通，接口矩阵全部有对应用例链接 |

## 公共 API / 接口 / 类型变更
1. 默认**不修改生产接口契约**（不强制补 `@RequestBody`、不调整当前错误状态码语义）。  
2. 仅增强测试层接口：`E2eHttpClient` 与场景辅助类型（测试专用）。  
3. 对业务错误优先断言响应体 `family/code`，对明确语义错误再断言 HTTP 状态码（如 401/404/409/400）。

## 测试场景与验收口径
1. 每个接口至少 1 条主流程用例。  
2. 每个接口至少 1 条常见错误用例；若接口本身无业务分支，至少覆盖“未登录访问”错误。  
3. 异步任务类接口必须包含轮询等待与超时失败信息。  
4. 媒体接口同时覆盖非 Range 与 Range。  
5. 最终执行口径：`/Users/yang/Documents/code/UniRhy/server` 下至少运行 `./gradlew :api-e2e:test`，并按项目约定运行 `./gradlew compileKotlin`。

## 假设与默认决策
1. 覆盖深度采用你选择的“主流程 + 常见错误”。  
2. 契约策略采用你选择的“先按现状补测”。  
3. 不跨项目改造（仅 `server` 工程内的 `api-e2e` 与必要测试支持代码）。  
4. 测试环境继续依赖真实 PostgreSQL 临时库与本地音频样本路径（`E2E_SCAN_PATH`）。
