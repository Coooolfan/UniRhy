//! Thin wrapper around [`tauri_plugin_notification`] for fire-and-forget
//! local notifications.
//!
//! Errors are logged but never propagated — callers should not need to
//! handle notification failures.

use tauri::{AppHandle, Runtime};
use tauri_plugin_notification::NotificationExt;

/// Thin wrapper over `tauri-plugin-notification`.
///
/// Fire-and-forget: errors are logged via `log::warn!` and never propagated.
#[derive(Clone)]
pub struct Notifier<R: Runtime> {
    pub(crate) app: AppHandle<R>,
}

impl<R: Runtime> Notifier<R> {
    /// Show a local notification with the given title and body.
    ///
    /// Errors are logged but not returned — callers should not need to
    /// handle notification failures.
    pub fn show(&self, title: &str, body: &str) {
        if let Err(e) = self
            .app
            .notification()
            .builder()
            .title(title)
            .body(body)
            .show()
        {
            log::warn!("background-service: notification failed: {e}");
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Compile-time test: Notifier can be constructed and cloned from an AppHandle.
    /// (Does not call show() because that requires a running Tauri app.)
    #[allow(dead_code)]
    fn notifier_clone_compiles<R: Runtime + Clone>(app: AppHandle<R>) {
        let n = Notifier { app };
        let _cloned = n.clone();
    }
}
