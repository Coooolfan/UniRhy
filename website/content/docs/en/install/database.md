---
title: Bring Your Own Database
description: Connect UniRhy to an existing external PostgreSQL instance.
---

The default [Docker installation](/en/docs/install/docker) bundles a PostgreSQL container for an out-of-the-box deployment. To integrate with an existing external PostgreSQL — a managed RDS instance, a shared internal cluster, etc. — UniRhy can connect directly to that external instance without starting the bundled container.

> Only connect to a PostgreSQL instance that is fully under your control. Future releases may require server-side extensions or plugins to be installed on the database, and managed RDS offerings do not always permit that.

## Version

- **PostgreSQL 17+**
- Encoding must be `UTF8`

## Create database and role

Execute the following on the target PostgreSQL instance:

```sql
CREATE USER unirhy WITH PASSWORD 'your-strong-password';
CREATE DATABASE unirhy OWNER unirhy ENCODING 'UTF8';
```

UniRhy initializes the required tables on first start — **no manual schema setup is needed**.

## Connection settings

Connection parameters are read from the following environment variables:

| Variable      | Required | Description                           |
| ------------- | -------- | ------------------------------------- |
| `DB_HOST`     | yes      | Database host or IP                   |
| `DB_PORT`     | no       | Port, defaults to `5432`              |
| `POSTGRES_DB` | no       | Database name, defaults to `unirhy`   |
| `DB_USER`     | no       | Database role, defaults to `postgres` |
| `DB_PASSWORD` | yes      | Database password                     |

The JDBC URL is composed from these:

```
jdbc:postgresql://${DB_HOST}:${DB_PORT}/${POSTGRES_DB}
```

## Adjust compose.yml

**Remove the `db` service and the `pgdata` volume** from `compose.yml`, and supply the connection variables under `app.environment`:

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

Then start with `docker compose up -d`.
