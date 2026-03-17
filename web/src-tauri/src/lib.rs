mod config;
mod proxy;

use proxy::ProxyServer;
use serde::Serialize;
use tauri::Manager;

struct AppState {
    proxy_port: u16,
    proxy: ProxyServer,
}

#[derive(Serialize)]
struct RuntimeConfig {
    proxy_url: String,
    platform: String,
}

fn detect_platform() -> String {
    if cfg!(target_os = "macos") {
        "macos".to_string()
    } else if cfg!(target_os = "windows") {
        "windows".to_string()
    } else {
        "linux".to_string()
    }
}

#[tauri::command]
fn get_runtime_config(state: tauri::State<'_, AppState>) -> RuntimeConfig {
    RuntimeConfig {
        proxy_url: format!("http://localhost:{}", state.proxy_port),
        platform: detect_platform(),
    }
}

#[tauri::command]
async fn get_backend_url(state: tauri::State<'_, AppState>) -> Result<String, String> {
    let url = state.proxy.backend_url().read().await.clone();
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
    let mut backend = state.proxy.backend_url().write().await;
    *backend = normalized.clone();
    Ok(normalized)
}

pub fn run() {
    tauri::Builder::default()
        .setup(|app| {
            let backend_url = config::load_backend_url(app.handle());

            let proxy = tauri::async_runtime::block_on(ProxyServer::start(backend_url));

            let port = proxy.port();
            app.manage(AppState {
                proxy_port: port,
                proxy,
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
