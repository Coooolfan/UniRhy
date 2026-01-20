# UniRhy 接口测试与验证体系改造计划

## 1. 背景与问题

现有接口调试与全流程验证主要依赖 GUI 工具（如 Apifox）。该方式在早期验证阶段便捷，但在持续迭代中暴露出明显维护成本与工程化不足：

* 需要在工具内维护一套请求体/变量配置，与后端 DTO/OpenAPI 出现“双份真相”
* 缺少强类型与格式校验，错误往往在运行期暴露
* 跨请求变量（token、id 等）缺少 IDE 级补全、重构与上下文能力
* 流程用例难以工程化沉淀（代码审查、版本管理、CI 报告等能力较弱）

项目后端已具备良好前置条件：入参/出参高度类型化，且基于 Jimmer 可提供接近完整的 OpenAPI/Swagger 文档，可支撑“以契约为单一事实源”的工程化测试体系。

---

## 2. 目标

建立一套可维护、可扩展的接口验证体系，实现：

* **单一事实源**：以 Jimmer OpenAPI 文档作为请求/响应契约来源，避免重复维护请求体
* **强类型与校验**：测试用例在编译期获得类型约束，运行期可进行结构/字段断言
* **流程可编排**：支持全流程链路（token 串联、变量传递、步骤复用）
* **本地优先，兼容 CI**：本地可快速运行；CI 能跑则运行 smoke（冒烟）集，跑不了不阻塞研发
* **可审查可追溯**：测试资产纳入代码仓库，支持 code review、版本管理、报告输出

---

## 3. 总体方案

### 3.1 测试资产形态

* 新增独立模块（建议 `api-e2e`），用于承载接口流程测试资产
* 保留 Apifox 作为“临时调试/接口浏览”工具，不再作为测试资产与流程的主存储

### 3.2 技术路线

* 以 `/api/openapi.yml`（由 Jimmer 提供）作为契约来源
* 使用 OpenAPI Generator 生成 Kotlin 强类型 Client + Models（避免手写请求体）
* 使用 Kotlin 测试框架（JUnit5 或 Kotest）编写流程场景测试（推荐 Kotest 便于场景化组织）
* 引入分层执行策略：

  * `smoke`：关键链路冒烟（适配 CI）
  * `full`：完整全流程回归（本地/夜间执行）

---

## 4. 代码与接口改造点（最小必要集）

为保证 OpenAPI 文档稳定、生成客户端可靠，需要进行少量接口语义明确化改造。

### 4.1 Controller 参数标注（必须）

* `AccountController.create(create: AccountCreate)`、`update(update: AccountUpdate)`：补充 `@RequestBody`
* `TokenController.login(email: String, password: String)`：至少补充 `@RequestParam`，明确为 query 参数

> 目的：避免 OpenAPI 对参数形态推断不稳定，从源头防止生成客户端“长歪”，降低维护风险。

### 4.2 登录返回 token 的改造（可选）

现接口登录方法无返回体，token 通过 cookie 下发；这会增加测试侧解析复杂度。建议在不破坏现有兼容的前提下优化为：

* 将登录接口改为 `POST /api/token`，请求体为 `LoginReq`，返回 `LoginResp(token=...)`

> 若短期不改，测试侧仍可通过解析响应 header/cookie 获取 token，但长期建议标准化。

### 4.3 token 传递规则固定

系统配置中 `sa-token.token-name = unirhy-token`，测试侧统一以该名称注入 token（Header 或 Cookie），并封装在客户端工厂中，避免散落在用例代码。

---

## 5. 测试工程实施说明（供实操执行）

### 5.1 模块结构建议

在项目根工程新增 `api-e2e` 模块，建议结构如下：

* `env/`：环境配置（local/ci）
* `client/`：客户端工厂（baseUrl、token 注入、序列化配置）
* `steps/`：业务步骤封装（登录、账户、配置等）
* `scenario/`：场景用例（smoke/full）

### 5.2 OpenAPI 文档管理策略（可选）

采取“稳定优先”的方式：

* 将 `openapi.yml` 固化到仓库（`api-e2e/src/test/resources/openapi/openapi.yml`）
* 提供脚本或 Gradle task 用于从运行环境拉取最新 spec 并更新（本地/CI 可选）

### 5.3 用例组织策略：步骤层 + 场景层

* 步骤层（steps）对 HTTP 细节收口：headers、token、序列化、错误处理
* 场景层（scenario）只表达业务流程：初始化 → 登录 → 查询/创建 → 校验 → 登出

### 5.4 数据隔离与可重复执行

为避免并发污染与互相踩数据：

* 每次运行生成 `runId`（时间戳/UUID）
* 业务数据命名携带 `runId` 前缀（如 `e2e-{runId}-xxx`）
* 清理策略作为可选项：若环境共享，建议在 teardown 阶段清理；若环境可重置，可不强制清理

---

## 6. 运行与交付方式

### 6.1 本地运行

* 通过环境变量或 `env/local.properties` 指定 `BASE_URL`、管理员账号等参数
* 默认运行 `full` 或指定执行 `smoke`

### 6.2 CI 运行

* CI 默认只运行 `smoke` 标签集合
* 若 CI 环境无法稳定访问被测服务，则该 job 可配置为非阻塞或暂时关闭，不影响本地验证体系的价值

### 6.3 输出与报告

* 使用 JUnit XML 作为基础报告输出（CI 常规兼容）
* 如需更友好可视化，可增配 Allure（作为加分项）

如需进一步提升可执行性，可在此计划通过后补充《实施清单》与《smoke 用例明细》（接口调用顺序、断言点、环境变量列表、失败判定规则），用于落地时逐条核对与验收。
