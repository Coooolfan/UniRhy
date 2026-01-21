# Jimmer notes

1. Jimmer 是一个高度依赖 ksp 技术的强类型 ORM 框架
2. Jimmer DTO：一个 `.dto` 文件只能 `export` 一个实体
3. Jimmer DTO：实体字段本身 nullable 时，input 字段不要再加 `?`
4. Jimmer 生成包路径：跟随实体所在包

   实体在 `com.coooolfan.unirhy.model.storage`，则生成代码在：

    - `com.coooolfan.unirhy.model.storage.dto`（DTO）
    - `com.coooolfan.unirhy.model.storage.by`（fetcher DSL 的 `by { ... }` 扩展）

5. `@FetchBy`：companion fetcher 需要显式声明为 `Fetcher<T>`
