---
title: About UniRhy
description: A self-hosted music streaming platform, designed for humans.
---

UniRhy is a **self-hosted music streaming platform**. It lets you run your own private music service on a server you control — a home NAS, a VPS, or any machine on your LAN — managing assets, serving audio, and synchronizing playback across devices.

## Core features

- **Fully self-hosted**: all data stays on your own server; no external service dependency.
- **Cross-platform clients**: Web, macOS, Windows, Android, iOS — all platforms, consistent.
- **Multi-device playback sync**: the same account keeps playback position in sync across devices, within the threshold of human perception.
- **Plugin extensions**: a unified, permission-aware, well-documented plugin system (WIP).
- **Library management**: scanning, organizing, transcoding, with both local and object storage.

## How it fits together

A UniRhy deployment consists of two services, both orchestrated in the provided `compose.yml` and shipped as Docker images:

- **Server**: hosts the frontend static assets and the backend, handling library management, audio delivery and cross-device sync, and managing local audio files.
- **Database**: handles data persistence.

Clients talk to the server over plain HTTP and WebSocket. As long as the clients can reach the server — a home NAS, a VPS, an LAN host — you are good to go.

## Project status

UniRhy is in **beta**. Core paths — playback, sync, library management — are usable and covered by end-to-end tests. Several advanced features remain on the roadmap. See [GitHub Releases](https://github.com/Coooolfan/UniRhy/releases) for the latest.
