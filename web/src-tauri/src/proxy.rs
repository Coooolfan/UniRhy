use std::sync::Arc;

use axum::body::Body;
use axum::extract::ws::{Message, WebSocket};
use axum::extract::{Request, State, WebSocketUpgrade};
use axum::response::{IntoResponse, Response};
use axum::routing::any;
use axum::Router;
use futures_util::stream::StreamExt;
use futures_util::SinkExt;
use reqwest::Client;
use tokio::net::TcpListener;
use tokio::sync::RwLock;

#[derive(Clone)]
struct ProxyState {
    backend_url: Arc<RwLock<String>>,
    client: Client,
}

pub struct ProxyServer {
    port: u16,
    backend_url: Arc<RwLock<String>>,
}

impl ProxyServer {
    pub async fn start(backend_url: String) -> Self {
        let backend_url = Arc::new(RwLock::new(backend_url));
        let state = ProxyState {
            backend_url: backend_url.clone(),
            client: Client::new(),
        };

        let app = Router::new()
            .route("/ws/{*path}", any(ws_proxy))
            .route("/api/{*path}", any(http_proxy))
            .with_state(state);

        let listener = TcpListener::bind("127.0.0.1:0").await.unwrap();
        let port = listener.local_addr().unwrap().port();

        tokio::spawn(async move {
            axum::serve(listener, app).await.ok();
        });

        Self { port, backend_url }
    }

    pub fn port(&self) -> u16 {
        self.port
    }

    pub fn backend_url(&self) -> &Arc<RwLock<String>> {
        &self.backend_url
    }
}

async fn http_proxy(State(state): State<ProxyState>, req: Request) -> Response {
    let backend = state.backend_url.read().await.clone();
    let uri = req.uri().to_string();
    let target_url = format!("{}{}", backend, uri);

    let method = req.method().clone();
    let mut builder = state.client.request(method, &target_url);

    for (name, value) in req.headers() {
        let name_str = name.as_str().to_lowercase();
        if name_str == "host" {
            continue;
        }
        builder = builder.header(name, value);
    }

    let body_bytes = match axum::body::to_bytes(req.into_body(), usize::MAX).await {
        Ok(b) => b,
        Err(_) => {
            return Response::builder()
                .status(502)
                .body(Body::from("Failed to read request body"))
                .unwrap();
        }
    };

    if !body_bytes.is_empty() {
        builder = builder.body(body_bytes);
    }

    let upstream_resp = match builder.send().await {
        Ok(r) => r,
        Err(_) => {
            return Response::builder()
                .status(502)
                .body(Body::from("Backend unreachable"))
                .unwrap();
        }
    };

    let status = upstream_resp.status();
    let mut response_builder = Response::builder().status(status);
    for (name, value) in upstream_resp.headers() {
        response_builder = response_builder.header(name, value);
    }

    let stream = upstream_resp.bytes_stream();
    response_builder
        .body(Body::from_stream(stream))
        .unwrap_or_else(|_| {
            Response::builder()
                .status(502)
                .body(Body::from("Failed to build response"))
                .unwrap()
        })
}

async fn ws_proxy(
    State(state): State<ProxyState>,
    ws: WebSocketUpgrade,
    req: Request,
) -> impl IntoResponse {
    let backend = state.backend_url.read().await.clone();
    let ws_backend = backend.replacen("http", "ws", 1);
    let uri = req.uri().to_string();
    let target_url = format!("{}{}", ws_backend, uri);

    ws.on_upgrade(move |client_socket| relay_ws(client_socket, target_url))
}

async fn relay_ws(client_socket: WebSocket, target_url: String) {
    let upstream = match tokio_tungstenite::connect_async(&target_url).await {
        Ok((stream, _)) => stream,
        Err(_) => return,
    };

    let (mut client_tx, mut client_rx) = client_socket.split();
    let (mut upstream_tx, mut upstream_rx) = upstream.split();

    use tokio_tungstenite::tungstenite::Message as TungMsg;

    tokio::select! {
        _ = async {
            while let Some(Ok(msg)) = client_rx.next().await {
                let tung_msg = match msg {
                    Message::Text(t) => TungMsg::Text(t.as_str().into()),
                    Message::Binary(b) => TungMsg::Binary(b.into()),
                    Message::Ping(p) => TungMsg::Ping(p.into()),
                    Message::Pong(p) => TungMsg::Pong(p.into()),
                    Message::Close(_) => break,
                };
                if upstream_tx.send(tung_msg).await.is_err() {
                    break;
                }
            }
        } => {}
        _ = async {
            while let Some(Ok(msg)) = upstream_rx.next().await {
                let axum_msg = match msg {
                    TungMsg::Text(t) => Message::Text(t.as_str().into()),
                    TungMsg::Binary(b) => Message::Binary(b.into()),
                    TungMsg::Ping(p) => Message::Ping(p.into()),
                    TungMsg::Pong(p) => Message::Pong(p.into()),
                    TungMsg::Close(_) => break,
                    _ => continue,
                };
                if client_tx.send(axum_msg).await.is_err() {
                    break;
                }
            }
        } => {}
    }
}
