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
  - 除非用户要求，每次更改后都应当在此项目中执行`yarn verify`以确保格式化与类型检查通过
- **官网**: 于 `./website` 文件夹. Vue, TypeScript, Vite.
  - 除非用户要求，每次更改后都应当在此项目中执行`yarn verify`以确保格式化与类型检查通过

# 注意事项

- 以中文作为第一语言与用户交流
- 除非用户主动要求，单次改动只能在单一项目中进行
- `./skills` 文件夹为技能包存放位置，其中包含某一领域的额外文档、脚本等
  - `./skills/skill-creator` 创建技能包：Guide for creating effective skills. This skill should be used when users want to create a new skill (or update an existing skill) that extends Claude's capabilities with specialized knowledge, workflows, or tool integrations.
  - `./skills/jimmer-orm` Jimmer ORM 开发指南：适用于：(1) 定义或修改 Jimmer 实体（使用 @Entity 注解的 interface）；(2) 编写 Jimmer DSL 查询代码；(3) 配置实体关联关系（@ManyToOne, @OneToMany, @ManyToMany）；(4) 使用动态谓词、动态表连接或隐式子查询；(5) 编辑包含 Jimmer 相关代码的 Java/Kotlin 文件。
  - `./skills/serverchan` Server酱 推送：通过 Server酱³ 发送推送通知。在无人值守开发时，需要向用户发送消息、通知、提醒时使用此 skill。从环境变量 FT07_KEY 获取密钥。
- 如果你需要访问开发环境数据库，请直接使用postgres相关工具，此工具已配置好连接信息与数据库
- yarn --cwd <project> <command> 用于指定工作路径
