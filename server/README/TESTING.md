# 测试约定

## 范围

- `server/src/test`：单元测试与集成测试，要求运行快，不依赖运行中的服务或外部系统。
- `server/api-e2e`：接口端到端流程回归测试，允许依赖运行中的服务与真实外部依赖。

## 规则

- 端到端测试不得放入 `server/src/test`。
- 单元测试与切片测试不得放入 `server/api-e2e`。
- CI 不运行 `api-e2e`。
- `api-e2e` 仅在本地或夜间任务运行 `full` 回归。

## `api-e2e` 运行说明

- `api-e2e` 的 `full` 使用真实 PostgreSQL，并在测试中创建临时数据库，测试结束后自动删除。
- 测试运行会创建临时工作目录，测试结束后自动删除，不会在机器上留下测试垃圾文件。
- 示例：`cd server && ./gradlew :api-e2e:test --tests com.unirhy.e2e.SystemAuthE2eTest`

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
- `E2eAssert`：HTTP 状态与业务错误断言封装。
- `E2eJson`：JSON 解析工具。
