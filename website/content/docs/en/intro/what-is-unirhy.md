---
title: What is UniRhy
description: A self-hosted music streaming platform that turns your local library into a multi-device playback service.
---

# What is UniRhy

UniRhy is a **self-hosted music streaming platform**. It turns a server you control — a home NAS, a VPS, or any machine on your LAN — into a private music backend: managing assets, serving audio, and synchronizing playback across devices.

## Core Features

- **Fully self-hosted**: all data stays on a server you control; no external service dependency.
- **Cross-platform clients**: Web, macOS, Windows, Android, iOS share a unified playback experience.
- **Multi-device playback sync**: the same account keeps playback position consistent across devices, with smooth join/leave behavior.
- **Plugin extensions**: integrate third-party metadata providers, transcoders, artist normalization and more via plugins.
- **Library management**: scanning, transcoding, organizing recordings by album/playlist/work, with both local and S3-compatible storage nodes.

## When UniRhy fits

- You own a sizable music collection (thousands to tens of thousands of tracks) and want centralized management instead of relying on cloud services.
- A household or small team wants a shared library while keeping individual playback state and preferences.
- You have privacy or licensing concerns and prefer not to use third-party streaming services.
- You want continuous playback across phone, desktop, and other endpoints.

## When UniRhy does not fit

- You are unwilling to operate any infrastructure: UniRhy needs a reachable server and a PostgreSQL instance.
- You expect a streaming catalog: UniRhy does not ship any licensed music — you need to own the audio files.
- You need DLNA / Cast / public share links: the current release focuses on private synchronized playback and does not include those.

## Project Status

UniRhy is in **beta**. Core paths — playback, sync, library management — are usable and covered by end-to-end tests. Several advanced features (AI playlist generation, vector-based recommendation) remain on the roadmap. See [GitHub Releases](https://github.com/Coooolfan/UniRhy/releases) for the latest.
