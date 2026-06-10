---
title: First Run
description: The initial setup wizard — admin account, storage node and scanning your first batch of music.
---

# First Run

After starting the server and opening `http://<your-host>:8654`, the frontend detects that the database is uninitialized and redirects you to `/init` for a two-step wizard.

## Part 01: Create the admin account

The first step creates the unique admin account. This account has full control of the instance — every later operation (creating other accounts, configuring storage, installing plugins) goes through it.

| Field    | Description                                                                                                |
| -------- | ---------------------------------------------------------------------------------------------------------- |
| Username | Login name. Keep it short and lowercase.                                                                   |
| Email    | Used for recovery and notifications (current release stores it only as an identifier — no email features). |
| Password | At least 8 characters. Mix case, digits and symbols.                                                       |

> The current release has no UI for resetting the admin password — store it safely.

## Part 02: System storage node

The second step sets the root path UniRhy uses for scan indexes, caches and transcoded outputs. The default `./data` is relative to the server working directory.

- **Docker**: the container working directory is `/app` by default. Strongly prefer a path inside a mounted volume, e.g. `/music`. Mount your host directory in `docker-compose.yml` first, then enter the in-container path here.
- **Bare metal**: provide an absolute path the server user can read and write, e.g. `/var/lib/unirhy/data`.

This path becomes UniRhy's **default local storage node**. Later in _Settings → Storage Nodes_ you can add more local nodes or S3-compatible OSS nodes; UniRhy supports scanning multiple storage nodes in parallel.

## Finishing the wizard

Clicking _Initialize_ redirects you to `/login`. Sign in with the admin account to reach the main UI.

## Next steps

Once inside, follow this order:

1. **Storage Nodes**: visit _Settings → Storage Nodes_ and confirm or add nodes.
2. **Import music**: drop your music files anywhere under the storage node root. The folder layout is not constrained — UniRhy organizes by embedded metadata.
3. **Trigger a scan**: open _Tasks_ and submit a `metadata-parse` task. UniRhy scans every unrecognized audio file.
4. **Optional: transcode**: submit a `transcode` task to derive Opus / MP3 versions of lossless audio for bandwidth-limited clients.
5. **Create more accounts**: under _Settings → Accounts_ add regular accounts for family or team.
6. **Sign in on clients**: open desktop or mobile clients, sign in with the same account, and start multi-device synchronized playback.

For deeper guides see [Library & Storage](/en/docs/usage/library) and [Playback Sync](/en/docs/usage/playback-sync).
