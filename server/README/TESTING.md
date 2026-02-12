# 测试约定

## 范围

- `server/src/test`：单元测试与集成测试，要求运行快，不依赖运行中的服务或外部系统。
- `server/api-e2e`：接口端到端流程测试，允许依赖运行中的服务与真实外部依赖。

## 规则

- 端到端测试不得放入 `server/src/test`。
- 单元测试与切片测试不得放入 `server/api-e2e`。
- CI 仅运行 `api-e2e` 的 smoke；完整 E2E 仅在本地或夜间任务运行。

## `api-e2e` 运行说明

- `api-e2e` 的 smoke 使用真实 PostgreSQL，并在测试中创建临时数据库，测试结束后自动删除。
- 文件系统扫描使用临时工作目录，测试结束后自动删除，不会在机器上留下测试垃圾文件。
- 扫描样本来源路径由环境变量 `E2E_SCAN_PATH` 控制，未设置时默认 `~/Music`。
- 默认值与 `ScanSamplePreparer` 中的默认路径保持一致。
- 示例：`E2E_SCAN_PATH=~/Music ./gradlew :api-e2e:test --tests com.unirhy.e2e.SmokeTest`

## `api-e2e` 覆盖矩阵

- 覆盖矩阵文件位于 `server/api-e2e/README/API_COVERAGE_MATRIX.md`，由测试生成器维护。
- 生成命令：`cd server && ./gradlew :api-e2e:generateCoverageMatrix`
- 校验命令：`cd server && ./gradlew :api-e2e:test`
- 当接口定义或覆盖登记发生变化时，测试会因矩阵过期而失败，需要先重新生成矩阵。

## `api-e2e` 代码分层

- 场景测试放在 `api-e2e/src/test/kotlin/com/unirhy/e2e`，仅描述业务步骤与断言。
- 公共基础设施放在 `api-e2e/src/test/kotlin/com/unirhy/e2e/support`，统一复用：
- `E2eRuntime`：临时数据库与工作目录生命周期管理。
- `E2eHttpClient`：带 Cookie 会话的 HTTP 调用封装。
- `ScanSamplePreparer`：扫描样本准备与路径解析（`E2E_SCAN_PATH`）。
- `E2eAwait`：轮询等待异步任务完成。
- `JsonNodeExtensions`：通用 JSON 查询工具。
