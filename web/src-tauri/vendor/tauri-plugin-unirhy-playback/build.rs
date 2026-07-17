const COMMANDS: &[&str] = &[
    "register_listener",
    "configure",
    "update_auth",
    "connect_sync",
    "disconnect_sync",
    "get_playback_state",
    "set_volume",
    "request_play",
    "request_pause",
    "request_seek",
    "request_sync_recovery",
    "local_set_queue",
    "local_play",
    "local_pause",
    "local_seek",
    "js_log",
];

fn main() {
    let mut builder = tauri_plugin::Builder::new(COMMANDS);

    // 仅在 Tauri CLI 驱动的移动端构建中注册 android 工程路径，
    // 避免裸 cargo 调用时触发移动端链接步骤。
    if std::env::var("TAURI_ANDROID_PROJECT_PATH").is_ok() {
        builder = builder.android_path("android");
    }

    if let Err(e) = builder.try_build() {
        panic!("{e:#}");
    }
}
