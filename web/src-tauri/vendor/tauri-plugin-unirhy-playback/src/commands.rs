use tauri::{command, AppHandle, Runtime};

use crate::error::Result;
use crate::models::*;
use crate::UnirhyPlaybackExt;

#[command]
pub(crate) async fn configure<R: Runtime>(
    app: AppHandle<R>,
    request: ConfigureRequest,
) -> Result<()> {
    app.unirhy_playback().configure(request)
}

#[command]
pub(crate) async fn update_auth<R: Runtime>(
    app: AppHandle<R>,
    request: UpdateAuthRequest,
) -> Result<()> {
    app.unirhy_playback().update_auth(request)
}

#[command]
pub(crate) async fn connect_sync<R: Runtime>(app: AppHandle<R>) -> Result<()> {
    app.unirhy_playback().connect_sync()
}

#[command]
pub(crate) async fn disconnect_sync<R: Runtime>(app: AppHandle<R>) -> Result<()> {
    app.unirhy_playback().disconnect_sync()
}

#[command]
pub(crate) async fn get_playback_state<R: Runtime>(app: AppHandle<R>) -> Result<serde_json::Value> {
    app.unirhy_playback().get_playback_state()
}

#[command]
pub(crate) async fn set_volume<R: Runtime>(
    app: AppHandle<R>,
    request: SetVolumeRequest,
) -> Result<()> {
    app.unirhy_playback().set_volume(request)
}

#[command]
pub(crate) async fn request_play<R: Runtime>(
    app: AppHandle<R>,
    request: RequestPlayRequest,
) -> Result<()> {
    app.unirhy_playback().request_play(request)
}

#[command]
pub(crate) async fn request_pause<R: Runtime>(
    app: AppHandle<R>,
    request: RequestPauseRequest,
) -> Result<()> {
    app.unirhy_playback().request_pause(request)
}

#[command]
pub(crate) async fn request_seek<R: Runtime>(
    app: AppHandle<R>,
    request: RequestSeekRequest,
) -> Result<()> {
    app.unirhy_playback().request_seek(request)
}

#[command]
pub(crate) async fn request_sync_recovery<R: Runtime>(app: AppHandle<R>) -> Result<()> {
    app.unirhy_playback().request_sync_recovery()
}

#[command]
pub(crate) async fn local_set_queue<R: Runtime>(
    app: AppHandle<R>,
    request: LocalSetQueueRequest,
) -> Result<()> {
    app.unirhy_playback().local_set_queue(request)
}

#[command]
pub(crate) async fn local_play<R: Runtime>(
    app: AppHandle<R>,
    request: LocalPlayRequest,
) -> Result<()> {
    app.unirhy_playback().local_play(request)
}

#[command]
pub(crate) async fn local_pause<R: Runtime>(app: AppHandle<R>) -> Result<()> {
    app.unirhy_playback().local_pause()
}

#[command]
pub(crate) async fn local_seek<R: Runtime>(
    app: AppHandle<R>,
    request: LocalSeekRequest,
) -> Result<()> {
    app.unirhy_playback().local_seek(request)
}

#[command]
pub(crate) async fn js_log<R: Runtime>(app: AppHandle<R>, request: JsLogRequest) -> Result<()> {
    app.unirhy_playback().js_log(request)
}
