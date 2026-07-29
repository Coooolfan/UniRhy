# 数据库迁移规范

## 基本规则

- 迁移文件位于 `server/src/main/resources/db/migration/`，由 Flyway 在服务启动时执行。
- 文件名格式固定为：

  ```
  V{数字}__{描述}.sql
  ```

- `{数字}` 是从 `1` 开始严格递增的迁移序号，只表示数据库迁移顺序，与应用版本号无关。
- `{描述}` 使用简短的英文 snake_case，例如 `V2__add_account_last_login.sql`。
- 每次数据库结构或基础数据发生变化时新增一个迁移文件，不跳号、不复用序号。
- 已提交到共享分支或已在任何环境执行的迁移文件不可修改、删除或重命名；后续修正必须新增迁移。
- `server/src/main/resources` 整体被 gitignore，但 `db/migration/*.sql` 已显式放行，新增迁移文件直接 `git add` 即可。
- 迁移 SQL 使用未限定的对象名，不得写死 `public.` 或其他 schema 前缀。

## Schema

- `DB_SCHEMA` 同时控制 PostgreSQL JDBC `currentSchema` 和 Flyway `default-schema`，默认值为 `public`。
- Flyway history、迁移创建的对象和 Jimmer 运行时查询必须位于同一个 schema。
- 自定义 schema 应使用小写 snake_case 标识符，例如 `unirhy` 或 `music_server`。
- Flyway 默认会尝试创建不存在的 schema；若迁移用户没有创建权限，部署前必须由数据库管理员创建并授权。

## 基线

- `V1__baseline.sql` 是当前数据库结构的新基线。
- 基线只负责在空数据库中直接创建当前所需的最终结构，不包含针对旧结构的 `ALTER`、数据搬迁或兼容逻辑。
- 使用旧迁移链创建的开发数据库不能直接切换到该基线，需要删除并重新创建数据库。
- 基线之后的第一个迁移文件为 `V2__{描述}.sql`，后续数字依次递增。

## 示例

```text
V1__baseline.sql
V2__add_account_last_login.sql
V3__create_listening_history.sql
```
