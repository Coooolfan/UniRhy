use serde::de::DeserializeOwned;
use tauri::{
    plugin::{PluginApi, PluginHandle},
    AppHandle, Runtime,
};

use crate::error::Result;
use crate::models::*;

pub fn init<R: Runtime, C: DeserializeOwned>(
    _app: &AppHandle<R>,
    api: PluginApi<R, C>,
) -> Result<UnirhyPlayback<R>> {
    let handle = api.register_android_plugin("app.unirhy.playback", "UnirhyPlaybackPlugin")?;
    Ok(UnirhyPlayback(handle))
}

/// Android 原生播放内核的 Rust 侧句柄，全部命令透传给 Kotlin 插件。
pub struct UnirhyPlayback<R: Runtime>(PluginHandle<R>);

impl<R: Runtime> UnirhyPlayback<R> {
    pub fn configure(&self, request: ConfigureRequest) -> Result<()> {
        self.0.run_mobile_plugin::<()>("configure", request)?;
        Ok(())
    }

    pub fn update_auth(&self, request: UpdateAuthRequest) -> Result<()> {
        self.0.run_mobile_plugin::<()>("updateAuth", request)?;
        Ok(())
    }

    pub fn connect_sync(&self) -> Result<()> {
        self.0.run_mobile_plugin::<()>("connectSync", ())?;
        Ok(())
    }

    pub fn disconnect_sync(&self) -> Result<()> {
        self.0.run_mobile_plugin::<()>("disconnectSync", ())?;
        Ok(())
    }

    pub fn get_playback_state(&self) -> Result<serde_json::Value> {
        Ok(self
            .0
            .run_mobile_plugin::<serde_json::Value>("getPlaybackState", ())?)
    }

    pub fn set_volume(&self, request: SetVolumeRequest) -> Result<()> {
        self.0.run_mobile_plugin::<()>("setVolume", request)?;
        Ok(())
    }

    pub fn request_play(&self, request: RequestPlayRequest) -> Result<()> {
        self.0.run_mobile_plugin::<()>("requestPlay", request)?;
        Ok(())
    }

    pub fn request_pause(&self, request: RequestPauseRequest) -> Result<()> {
        self.0.run_mobile_plugin::<()>("requestPause", request)?;
        Ok(())
    }

    pub fn request_seek(&self, request: RequestSeekRequest) -> Result<()> {
        self.0.run_mobile_plugin::<()>("requestSeek", request)?;
        Ok(())
    }

    pub fn request_sync_recovery(&self) -> Result<()> {
        self.0.run_mobile_plugin::<()>("requestSyncRecovery", ())?;
        Ok(())
    }

    pub fn local_set_queue(&self, request: LocalSetQueueRequest) -> Result<()> {
        self.0.run_mobile_plugin::<()>("localSetQueue", request)?;
        Ok(())
    }

    pub fn local_play(&self, request: LocalPlayRequest) -> Result<()> {
        self.0.run_mobile_plugin::<()>("localPlay", request)?;
        Ok(())
    }

    pub fn local_pause(&self) -> Result<()> {
        self.0.run_mobile_plugin::<()>("localPause", ())?;
        Ok(())
    }

    pub fn local_seek(&self, request: LocalSeekRequest) -> Result<()> {
        self.0.run_mobile_plugin::<()>("localSeek", request)?;
        Ok(())
    }
    pub fn js_log(&self, request: JsLogRequest) -> Result<()> {
        self.0.run_mobile_plugin::<()>("jsLog", request)?;
        Ok(())
    }

}
