# UniRhy Server `api-e2e` 测试工程规划

## 1. 范围与定位

- 本文档是 `server/api-e2e` 的唯一规划文档。
- 目标是建立可持续、可审查、可扩展的接口流程回归体系。

## 2. 当前基线

- 测试模块：`/Users/yang/Documents/code/UniRhy/server/api-e2e`
- 当前策略：`full` 本地/夜间执行，CI 不运行 `api-e2e`
- 覆盖矩阵：`/Users/yang/Documents/code/UniRhy/server/api-e2e/README/API_COVERAGE_MATRIX.md`
- 当前统计口径：按 `HTTP 方法 + Path + headers 条件` 计数

## 3. 目标

- 每个接口至少覆盖 1 条主流程用例。
- 每个接口至少覆盖 1 条常见错误用例；无业务分支接口至少覆盖鉴权失败路径。
- 回归执行具备可重复性、可追踪性、可维护性。
- 覆盖矩阵与控制器定义保持同步，变更可被自动校验阻断。

## 4. 工程结构与约定

- 场景层：`api-e2e/src/test/kotlin/com/unirhy/e2e`
  - 只描述业务步骤与断言，不处理底层 HTTP 细节。
- 支撑层：`api-e2e/src/test/kotlin/com/unirhy/e2e/support`
  - 统一封装会话、JSON、断言、运行时上下文、临时数据库与目录生命周期。
- 矩阵层：`api-e2e/src/test/kotlin/com/unirhy/e2e/support/matrix`
  - 自动扫描 Controller 端点、登记覆盖级别、渲染/校验矩阵文件。

## 5. 执行策略

- 本地执行：默认运行 `full` 回归。
- 夜间执行：运行全量 `api-e2e` 回归并产出测试报告。
- CI 策略：不运行 `api-e2e`，仅由单元/集成测试承担快速反馈。
- 数据隔离：每次运行使用独立临时数据库与临时工作目录，运行后自动清理。

## 6. 分阶段实施路线

1. 底座与清单
- 完善 `E2eHttpClient`、断言工具、场景状态与通用辅助能力。
- 保证覆盖矩阵与端点扫描机制稳定可用。

2. 系统与认证域
- 覆盖 `/api/system/config/*`、`/api/tokens*` 的主流程与错误路径。
- 建立会话闭环与未登录访问断言基线。

3. 存储配置域
- 覆盖 `/api/storage/fs/*`、`/api/storage/oss/*` 与系统配置联动。
- 覆盖创建、读取、更新、删除与关键约束错误。

4. 任务与内容读取域
- 覆盖 `/api/task/*`、`/api/works*`、`/api/albums*`、`/api/media/{id}`。
- 覆盖异步任务等待、读取链路和典型异常分支。

5. 账户与播放列表域
- 覆盖 `/api/accounts*`、`/api/playlists*`。
- 引入多账户场景，校验资源归属和越权访问。

6. 收敛与稳态
- 矩阵全量映射到测试用例引用。
- 失败信息标准化，回归执行时长与稳定性持续治理。

## 7. 验收口径

1. 覆盖矩阵中所有端点具备明确覆盖级别与用例引用。
2. `./gradlew :api-e2e:test` 稳定通过。
3. `./gradlew compileKotlin` 稳定通过。
4. 文档、代码与矩阵三者一致，不出现并行维护的冲突规则。

## 8. 运行命令

- 运行 `api-e2e`：`cd server && ./gradlew :api-e2e:test`
- 生成覆盖矩阵：`cd server && ./gradlew :api-e2e:generateCoverageMatrix`
- 编译校验：`cd server && ./gradlew compileKotlin`
