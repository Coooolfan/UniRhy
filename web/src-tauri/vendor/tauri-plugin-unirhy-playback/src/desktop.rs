use serde::de::DeserializeOwned;
use tauri::{plugin::PluginApi, AppHandle, Runtime};

use crate::error::{Error, Result};
use crate::models::*;

pub fn init<R: Runtime, C: DeserializeOwned>(
    _app: &AppHandle<R>,
    _api: PluginApi<R, C>,
) -> Result<UnirhyPlayback<R>> {
    Ok(UnirhyPlayback(std::marker::PhantomData))
}

/// 桌面平台占位实现：保证全平台编译通过，所有命令返回 UnsupportedPlatform。
pub struct UnirhyPlayback<R: Runtime>(std::marker::PhantomData<fn() -> R>);

impl<R: Runtime> UnirhyPlayback<R> {
    pub fn configure(&self, _request: ConfigureRequest) -> Result<()> {
        Err(Error::UnsupportedPlatform)
    }

    pub fn update_auth(&self, _request: UpdateAuthRequest) -> Result<()> {
        Err(Error::UnsupportedPlatform)
    }

    pub fn connect_sync(&self) -> Result<()> {
        Err(Error::UnsupportedPlatform)
    }

    pub fn disconnect_sync(&self) -> Result<()> {
        Err(Error::UnsupportedPlatform)
    }

    pub fn get_playback_state(&self) -> Result<serde_json::Value> {
        Err(Error::UnsupportedPlatform)
    }

    pub fn set_volume(&self, _request: SetVolumeRequest) -> Result<()> {
        Err(Error::UnsupportedPlatform)
    }

    pub fn request_play(&self, _request: RequestPlayRequest) -> Result<()> {
        Err(Error::UnsupportedPlatform)
    }

    pub fn request_pause(&self, _request: RequestPauseRequest) -> Result<()> {
        Err(Error::UnsupportedPlatform)
    }

    pub fn request_seek(&self, _request: RequestSeekRequest) -> Result<()> {
        Err(Error::UnsupportedPlatform)
    }

    pub fn request_sync_recovery(&self) -> Result<()> {
        Err(Error::UnsupportedPlatform)
    }

    pub fn local_set_queue(&self, _request: LocalSetQueueRequest) -> Result<()> {
        Err(Error::UnsupportedPlatform)
    }

    pub fn local_play(&self, _request: LocalPlayRequest) -> Result<()> {
        Err(Error::UnsupportedPlatform)
    }

    pub fn local_pause(&self) -> Result<()> {
        Err(Error::UnsupportedPlatform)
    }

    pub fn local_seek(&self, _request: LocalSeekRequest) -> Result<()> {
        Err(Error::UnsupportedPlatform)
    }
    pub fn js_log(&self, _request: JsLogRequest) -> Result<()> {
        Err(Error::UnsupportedPlatform)
    }

}
