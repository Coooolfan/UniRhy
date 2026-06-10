---
title: Docker
description: Launch UniRhy and PostgreSQL together with a single Docker Compose file.
---

# Docker

Docker is the recommended deployment shape for UniRhy. One `docker-compose.yml` brings up PostgreSQL and the UniRhy server together; the frontend static assets are served by the server itself, so no extra components are needed.

## Prerequisites

- Docker 24+
- Docker Compose v2
- At least 2 GB of free memory
- Disk space for music files and database data

## docker-compose.yml

Create a `docker-compose.yml` anywhere:

```yaml
services:
  db:
    image: postgres:17
    restart: unless-stopped
    environment:
      POSTGRES_DB: unirhy
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: ${DB_PASSWORD:?DB_PASSWORD is required}
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ['CMD-SHELL', 'pg_isready -U postgres -d unirhy']
      interval: 5s
      timeout: 5s
      retries: 5

  app:
    image: coooolfan/unirhy:latest
    restart: unless-stopped
    depends_on:
      db:
        condition: service_healthy
    environment:
      SERVER_PORT: 8654
      DB_HOST: db
      DB_PORT: 5432
      POSTGRES_DB: unirhy
      DB_USER: postgres
      DB_PASSWORD: ${DB_PASSWORD:?DB_PASSWORD is required}
      SA_TOKEN_JWT_SECRET_KEY: ${SA_TOKEN_JWT_SECRET_KEY}
      UNIRHY_MEDIA_SIGNING_KEY: ${UNIRHY_MEDIA_SIGNING_KEY}
      UNIRHY_CORS_ALLOWED_ORIGINS: ${UNIRHY_CORS_ALLOWED_ORIGINS:-http://localhost:8654}
    volumes:
      - ./music:/music
    ports:
      - '8654:8654'

volumes:
  pgdata:
```

> Images are published to DockerHub as `coooolfan/unirhy`, tagged to match GitHub releases (e.g. `0.1.0-beta.1`, `latest`). Pin a specific version in production rather than using `latest`.

## Environment variables

Create a `.env` next to `docker-compose.yml`:

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

To generate a random key:

```sh
openssl rand -hex 32
```

## Start

```sh
docker compose up -d
```

On first start the server runs Flyway migrations to create the schema. After 10-30 seconds, open:

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

Each release applies its migrations automatically on container start. Back up the PostgreSQL volume before upgrading. See [Upgrade](/en/docs/install/upgrade) for details.
