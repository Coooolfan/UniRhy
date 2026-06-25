---
title: About UniRhy
description: A self-hosted music streaming platform that turns your local library into a multi-device playback service.
---

UniRhy is a **self-hosted music streaming platform**. It turns a server you control — a home NAS, a VPS, or any machine on your LAN — into a private music backend: managing assets, serving audio, and synchronizing playback across devices.

## Core features

- **Fully self-hosted**: all data stays on a server you control; no external service dependency.
- **Cross-platform clients**: Web, macOS, Windows, Android, iOS share a unified playback experience.
- **Multi-device playback sync**: the same account keeps playback position consistent across devices, with smooth join/leave behavior.
- **Plugin extensions**: integrate third-party metadata providers, transcoders, artist normalization and more via plugins.
- **Library management**: scanning, organizing, transcoding, with both local and object storage.

## How it fits together

A UniRhy deployment really only needs two things:

- **One server**: handles library management, audio delivery and cross-device sync, with a database alongside it.
- **One or more clients**: open in a browser, or install the macOS / Windows / Android / iOS apps.

Clients talk to the server over plain HTTP and WebSocket. As long as the clients can reach the server — a home NAS, a VPS, an LAN host — you are good to go.

Heavier capabilities (third-party metadata enrichment, artist name normalization, ...) come in as **plugins**: opt in when you need them, ignore them otherwise.

## Project status

UniRhy is in **beta**. Core paths — playback, sync, library management — are usable and covered by end-to-end tests. Several advanced features remain on the roadmap. See [GitHub Releases](https://github.com/Coooolfan/UniRhy/releases) for the latest.
