---
title: 数据库准备
description: PostgreSQL 版本要求、初始化、备份与维护建议。
---

# 数据库准备

UniRhy 当前只支持 **PostgreSQL**。

## 版本要求

- **PostgreSQL 17+**。其他版本未经测试。
- 推荐使用官方镜像 `postgres:17` 或对应的发行版包管理器版本。

> Docker Compose 部署的用户可以跳过本页大部分内容，按 [Docker 部署](/zh/docs/install/docker) 直接启动；本页面向裸机/独立数据库用户。

## 初始化

UniRhy 需要一个独立的数据库与可读写的用户：

```sql
CREATE USER unirhy WITH PASSWORD 'your-strong-password';
CREATE DATABASE unirhy OWNER unirhy ENCODING 'UTF8';
```

server 启动时会通过 Flyway 自动创建所有表与索引，**不需要预先建表**。

## 连接参数

server 通过以下环境变量连接数据库（默认值见 `server/src/main/resources/application.yaml`）：

| 变量          | 默认值     | 说明       |
| ------------- | ---------- | ---------- |
| `DB_HOST`     | `db`       | 数据库主机 |
| `DB_PORT`     | `5432`     | 数据库端口 |
| `POSTGRES_DB` | `unirhy`   | 数据库名   |
| `DB_USER`     | `postgres` | 数据库用户 |
| `DB_PASSWORD` | （必填）   | 数据库密码 |

JDBC URL 由这些值拼接而成：

```
jdbc:postgresql://${DB_HOST}:${DB_PORT}/${POSTGRES_DB}
```

如需更复杂的连接参数（SSL、连接池），后续可通过自定义 `application.yaml` 覆盖。

## 字符集与时区

- 数据库 encoding 必须为 `UTF8`（默认即是）。
- 服务器侧时区不影响 UniRhy 的业务逻辑；时间统一以 UTC 存储。

## 备份

UniRhy 不内置备份功能。推荐：

```sh
pg_dump -U unirhy -d unirhy -F c -f unirhy.dump
```

恢复：

```sh
pg_restore -U unirhy -d unirhy -c unirhy.dump
```

如果你使用 Docker Compose，注意备份 `pgdata` 卷或在容器内执行 `pg_dump`。

## 维护建议

- 启用 PostgreSQL 自动 `VACUUM`（默认开启），不需要额外配置。
- 媒体文件**不存储在数据库**，只存元数据与签名信息，所以数据库体量很小，单库通常不超过几百 MB。
- 不要直接改 UniRhy 的表结构——所有 schema 变更必须经 Flyway 迁移走，否则版本升级会失败。
