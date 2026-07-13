//! UniRhy Android 原生播放内核插件。
//!
//! Android 上承载 Media3 播放执行器与播放同步协议客户端（Kotlin 实现），
//! WebView 仅作为 UI 与遥控器；桌面平台注册占位实现以保证全平台编译。

use tauri::{
    plugin::{Builder, TauriPlugin},
    Manager, Runtime,
};

mod commands;
mod error;
mod models;

#[cfg(target_os = "android")]
mod mobile;
#[cfg(not(target_os = "android"))]
mod desktop;

pub use error::{Error, Result};
pub use models::*;

#[cfg(target_os = "android")]
use mobile::UnirhyPlayback;
#[cfg(not(target_os = "android"))]
use desktop::UnirhyPlayback;

pub trait UnirhyPlaybackExt<R: Runtime> {
    fn unirhy_playback(&self) -> &UnirhyPlayback<R>;
}

impl<R: Runtime, T: Manager<R>> UnirhyPlaybackExt<R> for T {
    fn unirhy_playback(&self) -> &UnirhyPlayback<R> {
        self.state::<UnirhyPlayback<R>>().inner()
    }
}

pub fn init<R: Runtime>() -> TauriPlugin<R> {
    Builder::new("unirhy-playback")
        .invoke_handler(tauri::generate_handler![
            commands::configure,
            commands::update_auth,
            commands::connect_sync,
            commands::disconnect_sync,
            commands::get_playback_state,
            commands::set_volume,
            commands::request_play,
            commands::request_pause,
            commands::request_seek,
            commands::request_sync_recovery,
            commands::local_set_queue,
            commands::local_play,
            commands::local_pause,
            commands::local_seek,
        ])
        .setup(|app, api| {
            #[cfg(target_os = "android")]
            let playback = mobile::init(app, api)?;
            #[cfg(not(target_os = "android"))]
            let playback = desktop::init(app, api)?;
            app.manage(playback);
            Ok(())
        })
        .build()
}
