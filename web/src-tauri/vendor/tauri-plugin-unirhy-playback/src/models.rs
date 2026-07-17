use serde::{Deserialize, Serialize};

/// 播放器工作模式：同步模式连接 WS 协议客户端，独立模式仅本地播放。
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum PlaybackMode {
    Sync,
    Independent,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ConfigureRequest {
    pub api_base_url: String,
    pub token: Option<String>,
    pub device_id: String,
    pub client_version: String,
    pub mode: PlaybackMode,
    pub preferred_asset_format: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateAuthRequest {
    pub token: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SetVolumeRequest {
    pub volume: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RequestPlayRequest {
    pub position_seconds: Option<f64>,
    pub current_index: Option<i32>,
    pub version: Option<i64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RequestPauseRequest {
    pub position_seconds: Option<f64>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RequestSeekRequest {
    pub position_seconds: f64,
}

/// 独立模式下由 TS 层下发的队列项，字段与队列快照的 CurrentQueueItemDto 对齐。
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalQueueItem {
    pub recording_id: i64,
    pub media_file_id: i64,
    pub title: String,
    pub artist_label: String,
    pub cover_url: Option<String>,
    pub duration_ms: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalSetQueueRequest {
    pub items: Vec<LocalQueueItem>,
    pub current_index: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalPlayRequest {
    pub current_index: i64,
    pub position_seconds: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct LocalSeekRequest {
    pub position_seconds: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct JsLogRequest {
    pub message: String,
}
