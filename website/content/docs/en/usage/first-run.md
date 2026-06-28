---
title: First Run
description: Initialize the instance — create the admin account and system storage node.
---

After starting the server, open `http://<your-host>:8654` (the port and host match the mapping declared in `compose.yml`). When the database has not been initialized, the frontend redirects to `/init` and enters a two-step wizard.

## Step 1: Create the admin account

The first step creates the unique admin account. This account holds full control of the instance — every later operation (creating other accounts, configuring storage, installing plugins) goes through it.

| Field    | Description                                                                                                |
| -------- | ---------------------------------------------------------------------------------------------------------- |
| Username | Login name. Keep it short and lowercase.                                                                   |
| Email    | Used for recovery and notifications (current release stores it only as an identifier — no email features). |
| Password | At least 8 characters. Mix case, digits and symbols.                                                       |

> The current release has no UI for resetting the admin password — store it safely.

## Step 2: System storage node

The second step sets the root path UniRhy uses for scan indexes, caches and transcoded outputs.

> To avoid cluttering your existing music library folder, set the system storage node to an **empty folder** and add your music library folder as a separate storage node later in the settings.

- **Docker**: provide the absolute in-container path of a mounted volume. With the recommended [Docker installation](/en/docs/install/docker), `compose.yml` mounts the host `./data` to `/data` inside the container, so enter `/data` here. Paths outside the mounted volume are lost when the container is removed.
- **Bare metal**: provide an absolute path the server user can read and write, e.g. `/var/lib/unirhy/data`.

This path is registered as UniRhy's **default local storage node**. More local or S3-compatible OSS nodes can be added later under _Settings → Storage Nodes_; UniRhy supports scanning multiple storage nodes in parallel.

## Finishing the wizard

Clicking _Initialize_ redirects to `/login`. Sign in with the admin account to reach the main UI.

## Next steps

Once inside, the recommended order is:

1. **Storage Nodes**: visit _Settings → Storage Nodes_ and confirm or add nodes.
2. **Add your music folder**: add your existing music folder as a new read-only storage node. The folder layout is not constrained — UniRhy organizes by embedded metadata.
3. **Trigger a scan**: open _Tasks_ and submit a `metadata-parse` task to scan every unrecognized audio file.
4. **Optional: transcode**: submit a `transcode` task to derive Opus / MP3 versions of lossless audio for bandwidth-limited clients.
5. **Create more accounts**: under _Settings → Accounts_ add regular accounts for family or team members.
