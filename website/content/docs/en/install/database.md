---
title: Database
description: PostgreSQL version requirements, initialization, backup and maintenance.
---

# Database

UniRhy currently supports **PostgreSQL** only.

## Version

- **PostgreSQL 17+**. Other versions are untested.
- Use the official `postgres:17` image or the equivalent OS package.

> If you deploy via Docker Compose you can skip most of this page — see [Docker installation](/en/docs/install/docker) directly. This page is for bare-metal / managed-database users.

## Initialization

UniRhy needs its own database and a user with full access:

```sql
CREATE USER unirhy WITH PASSWORD 'your-strong-password';
CREATE DATABASE unirhy OWNER unirhy ENCODING 'UTF8';
```

On startup the server runs Flyway migrations to create all tables and indexes automatically — **no manual schema setup is needed**.

## Connection parameters

The server reads these environment variables (defaults from `server/src/main/resources/application.yaml`):

| Variable      | Default    | Description       |
| ------------- | ---------- | ----------------- |
| `DB_HOST`     | `db`       | Database host     |
| `DB_PORT`     | `5432`     | Database port     |
| `POSTGRES_DB` | `unirhy`   | Database name     |
| `DB_USER`     | `postgres` | Database user     |
| `DB_PASSWORD` | (required) | Database password |

The JDBC URL is composed from these:

```
jdbc:postgresql://${DB_HOST}:${DB_PORT}/${POSTGRES_DB}
```

For advanced connection options (SSL, pool settings) override `application.yaml` with your own.

## Encoding and timezone

- Database encoding must be `UTF8` (default).
- Server timezone does not affect UniRhy logic; timestamps are stored in UTC.

## Backup

UniRhy ships no built-in backup. Recommended:

```sh
pg_dump -U unirhy -d unirhy -F c -f unirhy.dump
```

Restore:

```sh
pg_restore -U unirhy -d unirhy -c unirhy.dump
```

For Docker Compose deployments, either back up the `pgdata` volume or run `pg_dump` inside the container.

## Maintenance

- PostgreSQL auto `VACUUM` is enabled by default — no extra configuration needed.
- Media files are **not stored in the database**, only metadata and signatures. The database stays small, typically under a few hundred MB.
- Never modify UniRhy tables manually — every schema change must go through Flyway migrations, or upgrades will fail.
