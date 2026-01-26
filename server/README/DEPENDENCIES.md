# Server 依赖库清单

本文档列出了 UniRhy Server 子项目所使用的所有依赖库及其开源协议。

## 运行时依赖

| 依赖库 | 版本 | 开源协议 | 描述 |
|--------|------|----------|------|
| Spring Boot Starter WebMvc | 4.0.1 | Apache-2.0 | Spring MVC Web 框架 |
| Spring Boot Jackson2 | 4.0.1 | Apache-2.0 | JSON 序列化支持 |
| Spring Boot Starter Flyway | 4.0.1 | Apache-2.0 | 数据库迁移工具集成 |
| Spring Security Crypto | - | Apache-2.0 | 加密工具库 |
| Jackson Module Kotlin | - | Apache-2.0 | Kotlin 的 Jackson 支持模块 |
| Kotlin Reflect | 2.3.0 | Apache-2.0 | Kotlin 反射库 |
| Jimmer Spring Boot Starter | 0.9.120 | Apache-2.0 | ORM 框架 |
| Jimmer KSP | 0.9.120 | Apache-2.0 | Jimmer 代码生成处理器 |
| Sa-Token Spring Boot3 Starter | 1.44.0 | Apache-2.0 | 轻量级权限认证框架 |
| PostgreSQL JDBC Driver | - | BSD-2-Clause | PostgreSQL 数据库驱动 |
| Flyway Database PostgreSQL | - | Apache-2.0 | Flyway PostgreSQL 支持 |

## 构建工具与插件

| 插件 | 版本 | 开源协议 | 描述 |
|------|------|----------|------|
| Kotlin JVM | 2.3.0 | Apache-2.0 | Kotlin JVM 编译插件 |
| Kotlin Spring | 2.3.0 | Apache-2.0 | Kotlin Spring 整合插件 |
| Spring Boot Gradle Plugin | 4.0.1 | Apache-2.0 | Spring Boot 构建插件 |
| Spring Dependency Management | 1.1.7 | Apache-2.0 | 依赖管理插件 |
| GraalVM Native Build Tools | 0.11.3 | UPL-1.0 | GraalVM 原生镜像构建工具 |
| KSP (Kotlin Symbol Processing) | 2.3.4 | Apache-2.0 | Kotlin 符号处理器 |

## 测试依赖

| 依赖库 | 版本 | 开源协议 | 描述 |
|--------|------|----------|------|
| Spring Boot Starter WebMvc Test | 4.0.1 | Apache-2.0 | Web MVC 测试支持 |
| Spring Boot Starter Flyway Test | 4.0.1 | Apache-2.0 | Flyway 测试支持 |
| Kotlin Test JUnit5 | 2.3.0 | Apache-2.0 | Kotlin JUnit5 测试库 |
| JUnit Platform Launcher | - | EPL-2.0 | JUnit 平台启动器 |

## 协议说明

- **Apache-2.0**: Apache License 2.0 - 允许商业使用、修改、分发，需保留版权和许可声明
- **BSD-2-Clause**: BSD 2-Clause License - 允许商业使用、修改、分发，需保留版权声明
- **UPL-1.0**: Universal Permissive License 1.0 - Oracle 通用许可协议，与 MIT 兼容
- **EPL-2.0**: Eclipse Public License 2.0 - 允许商业使用，修改需开源
