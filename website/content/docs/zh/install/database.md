---
title: 自有数据库
description: 让 UniRhy 连接到外部已有的 PostgreSQL 实例。
---

[Docker 部署](/zh/docs/install/docker) 默认随附 PostgreSQL 容器，开箱即用。若需对接外部已有的 PostgreSQL（托管 RDS、内网共享实例等），可让 UniRhy 直接连接外部实例，无需启动内置容器。

> 建议仅连接到可完全掌控的 PostgreSQL 实例。后续版本可能引入需要在数据库侧加载的扩展或插件，托管 RDS 不一定提供此类能力。

## 版本要求

- **PostgreSQL 17+**，其他版本未经测试。
- Encoding 必须为 `UTF8`。

## 创建数据库与用户

在目标 PostgreSQL 实例上执行：

```sql
CREATE USER unirhy WITH PASSWORD 'your-strong-password';
CREATE DATABASE unirhy OWNER unirhy ENCODING 'UTF8';
```

UniRhy 在首次启动时自动初始化所需表结构，**无需预先建表**。

## 连接配置

UniRhy 通过以下环境变量读取连接参数：

| 变量          | 必填 | 说明                          |
| ------------- | ---- | ----------------------------- |
| `DB_HOST`     | 是   | 数据库主机或 IP               |
| `DB_PORT`     | 否   | 端口，默认 `5432`             |
| `POSTGRES_DB` | 否   | 数据库名，默认 `unirhy`       |
| `DB_USER`     | 否   | 数据库用户，默认 `postgres`   |
| `DB_PASSWORD` | 是   | 数据库密码                    |

JDBC URL 由上述变量拼接：

```
jdbc:postgresql://${DB_HOST}:${DB_PORT}/${POSTGRES_DB}
```

## 调整 compose.yml

从 `compose.yml` 中**移除 `db` 服务与 `pgdata` 卷**，并在 `app.environment` 中补全连接变量：

```yaml
services:
  app:
    image: coolfan1024/unirhy:latest
    restart: unless-stopped
    environment:
      DB_HOST: postgres.internal.example.com
      DB_PORT: '5432'
      POSTGRES_DB: unirhy
      DB_USER: unirhy
      DB_PASSWORD: ${DB_PASSWORD}
      SA_TOKEN_JWT_SECRET_KEY: ${SA_TOKEN_JWT_SECRET_KEY}
      UNIRHY_MEDIA_SIGNING_KEY: ${UNIRHY_MEDIA_SIGNING_KEY}
      UNIRHY_CORS_ALLOWED_ORIGINS: ${UNIRHY_CORS_ALLOWED_ORIGINS}
    volumes:
      - ./data:/data
    ports:
      - '8654:8654'
```

随后 `docker compose up -d` 即可。
