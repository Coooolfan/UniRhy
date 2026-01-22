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

- 除非用户主动要求，单次改动只能在单一项目中进行
