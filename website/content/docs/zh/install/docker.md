---
title: Docker 部署
description: 使用 Docker Compose 一键启动 UniRhy 与配套 PostgreSQL。
---

Docker 是 UniRhy 推荐的部署形态。一份 `compose.yml`（即旧名 `docker-compose.yml`）同时拉起 PostgreSQL 与 UniRhy server，前端静态资源由 server 直接服务，无需额外组件。

## 前置要求

- Docker 24+
- Docker Compose v2
- 至少 2 GB 可用内存（转码通常需要较大的运行内存）
- 用于存放音乐文件与数据库数据的磁盘空间

## 准备 compose.yml

在任意目录新建 `compose.yml`：

```yaml
services:
  db:
    image: postgres:17
    restart: unless-stopped
    environment:
      POSTGRES_DB: unirhy
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data

  app:
    image: coolfan1024/unirhy:latest
    restart: unless-stopped
    depends_on: [db]
    environment:
      DB_PASSWORD: ${DB_PASSWORD}
      SA_TOKEN_JWT_SECRET_KEY: ${SA_TOKEN_JWT_SECRET_KEY}
      UNIRHY_MEDIA_SIGNING_KEY: ${UNIRHY_MEDIA_SIGNING_KEY}
      UNIRHY_CORS_ALLOWED_ORIGINS: ${UNIRHY_CORS_ALLOWED_ORIGINS}
    volumes:
      - ./data:/data
    ports:
      - '8654:8654'

volumes:
  pgdata:
```

## 准备环境变量

在 `compose.yml` 同目录下新建 `.env`：

```sh
# 数据库密码（必填）
DB_PASSWORD=your-strong-password

# 用户会话签名密钥（必填，建议 32 字节随机十六进制）
SA_TOKEN_JWT_SECRET_KEY=replace-with-random-hex

# 媒体文件 URL 签名密钥（必填，建议 32 字节随机十六进制）
UNIRHY_MEDIA_SIGNING_KEY=replace-with-random-hex

# 允许的前端来源（多个用逗号分隔）
UNIRHY_CORS_ALLOWED_ORIGINS=https://music.example.com
```

## 启动

```sh
docker compose up -d
```

首次启动时 server 会自动运行迁移建立 schema。等待 5~10 秒后访问：

```
http://<服务器地址>:8654
```

第一次访问会进入注册引导页，引导你创建管理员账号——详见 [首次启动](/zh/docs/usage/first-run)。

## 查看日志

```sh
docker compose logs -f app
```

## 停止与重启

```sh
docker compose down
docker compose up -d
```

数据库数据由 `pgdata` 卷持久化，`down` 不会丢失数据。如需彻底清除，使用 `docker compose down -v`。

## 升级到新版本

```sh
docker compose pull
docker compose up -d
```

每个版本的迁移会在容器启动时自动应用。升级前建议先备份 PostgreSQL 卷。
