---
title: Docker
description: Launch UniRhy and its bundled PostgreSQL with a single Docker Compose file.
---

Docker is the recommended deployment shape for UniRhy. One `compose.yml` (formerly `docker-compose.yml`) brings up PostgreSQL and the UniRhy server together; the frontend static assets are served by the server itself, so no extra components are needed.

## Prerequisites

- Docker 24+
- Docker Compose v2
- At least 2 GB of free memory (transcoding typically requires more)
- Disk space for music files and database data

## compose.yml

Create a `compose.yml` anywhere:

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

## Environment variables

Create a `.env` next to `compose.yml`:

```sh
# Database password (required)
DB_PASSWORD=your-strong-password

# Session signing key (required, 32-byte random hex recommended)
SA_TOKEN_JWT_SECRET_KEY=replace-with-random-hex

# Media URL signing key (required, 32-byte random hex recommended)
UNIRHY_MEDIA_SIGNING_KEY=replace-with-random-hex

# Allowed frontend origins (comma-separated)
UNIRHY_CORS_ALLOWED_ORIGINS=https://music.example.com
```

## Start

```sh
docker compose up -d
```

On first start the server initializes its schema automatically. After 5–10 seconds, open:

```
http://<your-host>:8654
```

The first visit lands on a setup page that guides you through creating an admin account — see [First run](/en/docs/usage/first-run).

## View logs

```sh
docker compose logs -f app
```

## Stop and restart

```sh
docker compose down
docker compose up -d
```

The `pgdata` volume persists data; `down` does not delete it. Use `docker compose down -v` to wipe everything.

## Upgrade

```sh
docker compose pull
docker compose up -d
```

Schema changes are applied automatically on container start. Back up the PostgreSQL volume before upgrading.
