# <img src="docs/logo.svg" alt="UniRhy Logo" width="32" valign="middle" /> UniRhy

UniRhy is a self-hosted music streaming platform organized as a monorepo with separated backend, frontend, and website projects. It aims to provide deployable and extensible music library management, playback, and synchronization capabilities.

English | [简体中文](docs/README.zh-CN.md)

> [!NOTE]
> This project is still in a very early stage. Feedback and contributions of all kinds are welcome, and existing features may still change significantly.

## Features

- Self-hosted deployment with no external service dependency.
- Music resource management.
- Cross-platform clients for Web, macOS, Android, iOS, and Windows.
- Playback state synchronization.
- Plugin-based extension support.

## Project Structure

- `server/`: Backend service based on Spring Boot, Kotlin, Gradle, and Jimmer ORM.
- `web/`: Main frontend client based on Vue, TypeScript, Vite, Pinia, and Tailwind CSS, including Tauri 2 desktop configuration.
- `website/`: Project website based on Vue, TypeScript, and Vite.
- `docker/`: Containerization files.
- `docs/`: Project-level supplementary documentation, including release notes under `docs/release_notes/`.
- `skills/`: Development helper skills and domain-specific conventions.

## Requirements

- JDK 25
- Node.js 24
- Yarn 4.12+
- PostgreSQL

## Quick Start

### Backend

```sh
cd server
./gradlew bootRun
```

### Frontend Client

```sh
cd web
yarn
yarn dev
```

### Website

```sh
cd website
yarn
yarn dev
```

## Documentation

- [Terminology Dictionary](docs/TERMINOLOGY.md)
- [Backend Testing Guide](server/README/TESTING.md)
- [Playback Sync Protocol](server/README/PLAYBACK_SYNC_PROTOCOL.md)
- [Playback Sync Plan](server/README/PLAYBACK_SYNC_PLAN.md)
- [Playback Sync Logging](server/README/PLAYBACK_SYNC_LOGGING.md)
- [Backend Dependencies](server/README/DEPENDENCIES.md)
- [Frontend Dependencies](web/README/DEPENDENCIES.md)

## License

See [LICENSE](LICENSE).
