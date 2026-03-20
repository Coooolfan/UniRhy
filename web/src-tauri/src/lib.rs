mod config;
mod proxy;

use proxy::ProxyServer;
use serde::Serialize;
use tauri::Manager;
use tokio::sync::RwLock;
use std::sync::Arc;

struct AppState {
    backend_url: Arc<RwLock<String>>,
    proxy_port: Option<u16>,
    _proxy: Option<ProxyServer>,
}

#[derive(Serialize)]
struct RuntimeConfig {
    proxy_url: String,
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
    let backend_url = tauri::async_runtime::block_on(async { state.backend_url.read().await.clone() });
    RuntimeConfig {
        proxy_url: state
            .proxy_port
            .map(|port| format!("http://localhost:{port}"))
            .unwrap_or(backend_url),
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
        .setup(|app| {
            let backend_url = Arc::new(RwLock::new(config::load_backend_url(app.handle())));
            let proxy = Some(tauri::async_runtime::block_on(ProxyServer::start(backend_url.clone())));
            app.manage(AppState {
                backend_url,
                proxy_port: proxy.as_ref().map(ProxyServer::port),
                _proxy: proxy,
            });

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
