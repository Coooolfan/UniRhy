use std::fs;
use std::path::PathBuf;

use serde::{Deserialize, Serialize};
use tauri::{AppHandle, Manager};

const DEFAULT_BACKEND_URL: &str = "http://localhost:8654";
const CONFIG_FILE_NAME: &str = "config.json";

#[derive(Serialize, Deserialize, Default)]
struct ConfigFile {
    #[serde(default)]
    backend_url: Option<String>,
}

fn config_path(app: &AppHandle) -> Option<PathBuf> {
    app.path().app_data_dir().ok().map(|dir| dir.join(CONFIG_FILE_NAME))
}

pub fn load_backend_url(app: &AppHandle) -> String {
    let path = match config_path(app) {
        Some(p) => p,
        None => return DEFAULT_BACKEND_URL.to_string(),
    };

    let content = match fs::read_to_string(&path) {
        Ok(c) => c,
        Err(_) => return DEFAULT_BACKEND_URL.to_string(),
    };

    let config: ConfigFile = match serde_json::from_str(&content) {
        Ok(c) => c,
        Err(_) => return DEFAULT_BACKEND_URL.to_string(),
    };

    config
        .backend_url
        .filter(|u| !u.trim().is_empty())
        .unwrap_or_else(|| DEFAULT_BACKEND_URL.to_string())
}

pub fn save_backend_url(app: &AppHandle, url: &str) -> Result<(), String> {
    let path = config_path(app).ok_or("Cannot resolve app data directory")?;

    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent).map_err(|e| format!("Failed to create config directory: {e}"))?;
    }

    let config = ConfigFile {
        backend_url: Some(url.to_string()),
    };

    let json = serde_json::to_string_pretty(&config).map_err(|e| format!("Serialize error: {e}"))?;
    fs::write(&path, json).map_err(|e| format!("Failed to write config: {e}"))?;

    Ok(())
}

pub fn normalize_backend_url(url: &str) -> Result<String, String> {
    let trimmed = url.trim();
    if trimmed.is_empty() {
        return Err("URL cannot be empty".to_string());
    }

    if !trimmed.starts_with("http://") && !trimmed.starts_with("https://") {
        return Err("URL must start with http:// or https://".to_string());
    }

    let normalized = trimmed.trim_end_matches('/').to_string();
    Ok(normalized)
}
