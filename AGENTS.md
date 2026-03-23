UniRhy (独一律) 是一个私有化的音乐流媒体平台

# 项目结构

- 此文件夹为项目根目录。使用 monorepo 管理多个工程，前后端分离。
- 所有子工程都有各自的`README`文件夹，每个 md 文件代表某个方面的说明，如果工作内容涉及对应方面，应当阅读对应 md 文件。

# 项目概述

- **后端**: 于 `./server` 文件夹. SpringBoot, Gradle, Kotlin, JVM 25.
  - 除非用户要求，每次更改后都应当在此项目中执行`./gradlew compileKotlin`以确保编译通过，如果因为JVM版本不兼容导致编译失败，请用户自行解决
  - 测试约定见 [server/README/TESTING.md](server/README/TESTING.md)
  - Jimmer ORM 开发指南与约定见 [skills/jimmer-orm/skills.md](skills/jimmer-orm/skills.md)
- **前端**: 于 `./web` 文件夹. Vue, TypeScript, Vite, Pinia, Tailwind CSS.
  - 除非用户要求，每次更改后都应当在此项目中执行`yarn verify && yarn format:check`以确保 Lint、类型检查与格式检查通过（若格式检查失败，先执行`yarn format`）
  - 此工程的 API 客户端(`/web/src/__generated`)由命令 `yarn api` 生成，在任何情况下都不允许改动此目录下的文件
  - Tauri 2 发行：除非用户要求，每次更改`/web/src-tauri/`后都应当在此项目中执行`yarn tauri:check`以确保类型检查通过
- **官网**: 于 `./website` 文件夹. Vue, TypeScript, Vite.
  - 除非用户要求，每次更改后都应当在此项目中执行`yarn verify && yarn format:check`以确保 Lint、类型检查与格式检查通过（若格式检查失败，先执行`yarn format`）

# 注意事项

- 当前分支处于 MVP 开发阶段，纯粹的结果导向，不追求代码的完美实现，不考虑兼容性，不关注并发安全、鲁棒性、性能等
- 任何情况下都不允许将此分支合并到其他分支
- 除非用户主动要求，单次改动只能在单一项目中进行
- `./skills` 文件夹为技能包存放位置，其中包含某一领域的额外文档、脚本等，先探索项目，再决定是否需要读取相关技能
  - `./skills/jimmer-orm` Jimmer ORM 开发指南：适用于：(1) 定义或修改 Jimmer 实体（使用 @Entity 注解的 interface）；(2) 编写 Jimmer DSL 查询代码；(3) 配置实体关联关系（@ManyToOne, @OneToMany, @ManyToMany）；(4) 使用动态谓词、动态表连接或隐式子查询；(5) 编辑包含 Jimmer 相关代码的 Java/Kotlin 文件。
- 所有描述性文字应该始终是面向 开发者/用户 的最终产物，不需要描述中间过程和演变原因。
- 除非用户主动要求，不需要考虑 API/数据库/模式 的向前兼容。
