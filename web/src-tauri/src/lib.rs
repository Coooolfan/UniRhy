mod config;

use async_trait::async_trait;
use serde::Serialize;
use std::sync::Arc;
use tauri::Manager;
use tauri_plugin_background_service::{BackgroundService, ServiceContext, ServiceError};
use tokio::sync::RwLock;

struct AppState {
    backend_url: Arc<RwLock<String>>,
}

struct AudioPlaybackService;

#[async_trait]
impl<R: tauri::Runtime> BackgroundService<R> for AudioPlaybackService {
    async fn init(&mut self, _ctx: &ServiceContext<R>) -> Result<(), ServiceError> {
        Ok(())
    }

    async fn run(&mut self, ctx: &ServiceContext<R>) -> Result<(), ServiceError> {
        ctx.shutdown.cancelled().await;
        Ok(())
    }
}

#[derive(Serialize)]
struct RuntimeConfig {
    backend_url: String,
    platform: String,
}

fn detect_platform() -> String {
    if cfg!(target_os = "macos") {
        "macos".to_string()
    } else if cfg!(target_os = "ios") {
        "ios".to_string()
    } else if cfg!(target_os = "windows") {
        "windows".to_string()
    } else if cfg!(target_os = "android") {
        "android".to_string()
    } else {
        "linux".to_string()
    }
}

#[tauri::command]
fn get_runtime_config(state: tauri::State<'_, AppState>) -> RuntimeConfig {
    let backend_url =
        tauri::async_runtime::block_on(async { state.backend_url.read().await.clone() });
    RuntimeConfig {
        backend_url,
        platform: detect_platform(),
    }
}

#[tauri::command]
async fn get_backend_url(state: tauri::State<'_, AppState>) -> Result<String, String> {
    let url = state.backend_url.read().await.clone();
    Ok(url)
}

#[tauri::command]
async fn set_backend_url(
    app: tauri::AppHandle,
    state: tauri::State<'_, AppState>,
    url: String,
) -> Result<String, String> {
    let normalized = config::normalize_backend_url(&url)?;
    config::save_backend_url(&app, &normalized)?;
    let mut backend = state.backend_url.write().await;
    *backend = normalized.clone();
    Ok(normalized)
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_http::init())
        .plugin(tauri_plugin_websocket::init())
        .plugin(tauri_plugin_notification::init())
        .plugin(tauri_plugin_android_battery_optimization::init())
        .plugin(tauri_plugin_background_service::init_with_service(|| {
            AudioPlaybackService
        }))
        .plugin(tauri_plugin_unirhy_playback::init())
        .setup(|app| {
            let backend_url = Arc::new(RwLock::new(config::load_backend_url(app.handle())));
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
