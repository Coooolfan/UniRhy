mod config;

use serde::Serialize;
use std::sync::RwLock;
use tauri::Manager;

struct AppState {
    backend_url: RwLock<String>,
}

#[derive(Serialize)]
struct RuntimeConfig {
    backend_url: String,
    platform: String,
}

fn detect_platform() -> String {
    std::env::consts::OS.to_string()
}

#[tauri::command]
fn get_runtime_config(state: tauri::State<'_, AppState>) -> RuntimeConfig {
    let backend_url = state.backend_url.read().expect("backend URL lock poisoned").clone();
    RuntimeConfig {
        backend_url,
        platform: detect_platform(),
    }
}

#[tauri::command]
fn get_backend_url(state: tauri::State<'_, AppState>) -> String {
    state.backend_url.read().expect("backend URL lock poisoned").clone()
}

#[tauri::command]
fn set_backend_url(
    app: tauri::AppHandle,
    state: tauri::State<'_, AppState>,
    url: String,
) -> Result<String, String> {
    let normalized = config::normalize_backend_url(&url)?;
    config::save_backend_url(&app, &normalized)?;
    let mut backend = state.backend_url.write().expect("backend URL lock poisoned");
    *backend = normalized.clone();
    Ok(normalized)
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let builder = tauri::Builder::default()
        .plugin(tauri_plugin_http::init())
        .plugin(tauri_plugin_websocket::init())
        .plugin(tauri_plugin_notification::init())
        .plugin(tauri_plugin_android_battery_optimization::init())
        .plugin(tauri_plugin_unirhy_playback::init());

    // 扫码登录只在移动端提供，条码扫描与深链接插件亦仅支持 iOS/Android
    #[cfg(mobile)]
    let builder = builder
        .plugin(tauri_plugin_barcode_scanner::init())
        .plugin(tauri_plugin_deep_link::init());

    builder
        .setup(|app| {
            let backend_url = RwLock::new(config::load_backend_url(app.handle()));
            app.manage(AppState { backend_url });

            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            get_runtime_config,
            get_backend_url,
            set_backend_url,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
