use std::sync::Arc;

use axum::body::Body;
use axum::extract::ws::{Message, WebSocket};
use axum::extract::{Request, State, WebSocketUpgrade};
use axum::http::{HeaderMap, HeaderValue, Method, StatusCode};
use axum::response::{IntoResponse, Response};
use axum::routing::any;
use axum::Router;
use futures_util::stream::StreamExt;
use futures_util::SinkExt;
use reqwest::Client;
use tokio::net::TcpListener;
use tokio::sync::RwLock;

const MEDIA_AUTH_TOKEN_QUERY_PARAM: &str = "unirhy-proxy-token";
const TOKEN_HEADER_NAME: &str = "unirhy-token";

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
    let origin = req.headers().get("origin").cloned();
    let request_headers = req
        .headers()
        .get("access-control-request-headers")
        .cloned();

    if req.method() == Method::OPTIONS {
        let mut response = Response::builder()
            .status(StatusCode::NO_CONTENT)
            .body(Body::empty())
            .unwrap();
        apply_cors_headers(response.headers_mut(), origin.as_ref(), request_headers.as_ref());
        return response;
    }

    let backend = state.backend_url.read().await.clone();
    let uri = req.uri().to_string();
    let (target_url, proxy_token) = build_upstream_request(&backend, &uri);

    let method = req.method().clone();
    let mut builder = state.client.request(method, &target_url);

    for (name, value) in req.headers() {
        let name_str = name.as_str().to_lowercase();
        if name_str == "host" {
            continue;
        }
        builder = builder.header(name, value);
    }
    if let Some(token) = proxy_token {
        builder = builder.header(TOKEN_HEADER_NAME, token);
    }

    let body_bytes = match axum::body::to_bytes(req.into_body(), usize::MAX).await {
        Ok(b) => b,
        Err(_) => {
            let mut response = Response::builder()
                .status(502)
                .body(Body::from("Failed to read request body"))
                .unwrap();
            apply_cors_headers(response.headers_mut(), origin.as_ref(), request_headers.as_ref());
            return response;
        }
    };

    if !body_bytes.is_empty() {
        builder = builder.body(body_bytes);
    }

    let upstream_resp = match builder.send().await {
        Ok(r) => r,
        Err(err) => {
            let mut response = Response::builder()
                .status(502)
                .body(Body::from(format!("Backend unreachable: {err}")))
                .unwrap();
            apply_cors_headers(response.headers_mut(), origin.as_ref(), request_headers.as_ref());
            return response;
        }
    };

    let status = upstream_resp.status();
    let mut response_builder = Response::builder().status(status);
    for (name, value) in upstream_resp.headers() {
        response_builder = response_builder.header(name, value);
    }

    let stream = upstream_resp.bytes_stream();
    let mut response = response_builder
        .body(Body::from_stream(stream))
        .unwrap_or_else(|_| {
            Response::builder()
                .status(502)
                .body(Body::from("Failed to build response"))
                .unwrap()
        });
    apply_cors_headers(response.headers_mut(), origin.as_ref(), request_headers.as_ref());
    response
}

fn build_upstream_request(backend: &str, uri: &str) -> (String, Option<String>) {
    let mut target_url = reqwest::Url::parse(&format!("{}{}", backend, uri))
        .expect("proxy target URL should always be valid");
    let mut proxy_token = None;
    let mut retained_query_pairs = Vec::new();

    if target_url.query().is_some() {
        for (key, value) in target_url.query_pairs() {
            if key == MEDIA_AUTH_TOKEN_QUERY_PARAM {
                proxy_token.get_or_insert_with(|| value.into_owned());
                continue;
            }
            retained_query_pairs.push((key.into_owned(), value.into_owned()));
        }

        target_url.set_query(None);
        if !retained_query_pairs.is_empty() {
            let mut query_pairs = target_url.query_pairs_mut();
            for (key, value) in retained_query_pairs {
                query_pairs.append_pair(&key, &value);
            }
        }
    }

    (target_url.into(), proxy_token)
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

fn apply_cors_headers(
    headers: &mut HeaderMap,
    origin: Option<&HeaderValue>,
    request_headers: Option<&HeaderValue>,
) {
    if let Some(origin) = origin {
        headers.insert("access-control-allow-origin", origin.clone());
        headers.insert("access-control-allow-credentials", HeaderValue::from_static("true"));
    }

    headers.insert(
        "access-control-allow-methods",
        HeaderValue::from_static("GET,POST,PUT,PATCH,DELETE,HEAD,OPTIONS"),
    );
    headers.insert(
        "access-control-allow-headers",
        request_headers
            .cloned()
            .unwrap_or_else(|| HeaderValue::from_static("content-type, tenant, unirhy-token")),
    );
    headers.insert("vary", HeaderValue::from_static("Origin"));
    headers.append("vary", HeaderValue::from_static("Access-Control-Request-Method"));
    headers.append("vary", HeaderValue::from_static("Access-Control-Request-Headers"));
}
