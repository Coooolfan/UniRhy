# UniRhy background service patch

Source: `tauri-plugin-background-service` 0.7.1.

This vendored copy carries Android fixes required by UniRhy until they are available upstream:

- `androidRestartOnProcessDeath` controls `START_STICKY` and persisted boot recovery intent.
- notification Stop and application-initiated service shutdown use distinct actions, so only the user action emits `androidNotificationStop`.

Remove this directory and restore the crates.io dependency after an upstream release provides equivalent behavior.
