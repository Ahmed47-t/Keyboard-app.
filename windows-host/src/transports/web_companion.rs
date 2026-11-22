use crate::protocol::{TransportMedium, WireFrame, WS_COMPANION_PORT};
use crate::session::HostHub;
use futures_util::{SinkExt, StreamExt};
use std::net::{Ipv4Addr, SocketAddrV4};
use std::sync::Arc;
use std::time::Duration;
use tokio::net::{TcpListener, TcpStream};
use tokio_tungstenite::{accept_async, tungstenite::Message};

pub async fn run_web_companion_server(hub: Arc<HostHub>) {
    let addr = SocketAddrV4::new(Ipv4Addr::UNSPECIFIED, WS_COMPANION_PORT);
    let Ok(listener) = TcpListener::bind(addr).await else {
        return;
    };

    loop {
        if let Ok((stream, _peer)) = listener.accept().await {
            let _ = stream.set_nodelay(true);
            let hub_clone = Arc::clone(&hub);
            tokio::spawn(async move {
                let _ = handle_ws_client(stream, hub_clone).await;
            });
        }
    }
}

async fn handle_ws_client(stream: TcpStream, hub: Arc<HostHub>) -> Result<(), String> {
    let mut ws = accept_async(stream).await.map_err(|e| e.to_string())?;

    let first_msg = tokio::time::timeout(Duration::from_secs(15), ws.next())
        .await
        .map_err(|_| "WS handshake timeout".to_string())?
        .ok_or_else(|| "WS closed".to_string())?
        .map_err(|e| e.to_string())?;

    let hello_frame: WireFrame = match first_msg {
        Message::Text(t) => serde_json::from_str(&t).map_err(|e| e.to_string())?,
        Message::Binary(b) => serde_json::from_slice(&b).map_err(|e| e.to_string())?,
        _ => return Err("Unexpected WS message".to_string()),
    };

    let (device_id, device_name, public_key_hex, client_nonce_hex, qr_token) = match hello_frame {
        WireFrame::ClientHello {
            device_id,
            device_name,
            public_key_hex,
            client_nonce_hex,
            qr_pairing_token,
            ..
        } => (
            device_id,
            device_name,
            public_key_hex,
            client_nonce_hex,
            qr_pairing_token,
        ),
        _ => return Err("Expected ClientHello".to_string()),
    };

    let (host_hello, cipher, approval_rx) = hub.begin_handshake(
        device_id.clone(),
        device_name.clone(),
        public_key_hex,
        client_nonce_hex,
        TransportMedium::Web,
        qr_token,
    )?;

    let hello_json = serde_json::to_string(&host_hello).map_err(|e| e.to_string())?;
    ws.send(Message::Text(hello_json))
        .await
        .map_err(|e| e.to_string())?;

    if let Some(rx) = approval_rx {
        let accepted = tokio::time::timeout(Duration::from_secs(90), rx)
            .await
            .map_err(|_| "Pairing timed out".to_string())?
            .unwrap_or(false);
        let decision = WireFrame::PairingDecision {
            accepted,
            reason: if accepted {
                None
            } else {
                Some("تم رفض الإقران".to_string())
            },
        };
        ws.send(Message::Text(
            serde_json::to_string(&decision).map_err(|e| e.to_string())?,
        ))
        .await
        .map_err(|e| e.to_string())?;
        if !accepted {
            return Ok(());
        }
    }

    let session_id = cipher.session_id();
    hub.activate_session(
        device_id.clone(),
        device_name,
        TransportMedium::Web,
        cipher.clone(),
    );

    let mut reply_seq = 0u64;
    while let Some(Ok(msg)) = ws.next().await {
        let raw_opt = match msg {
            Message::Text(t) => Some(t),
            Message::Binary(b) => String::from_utf8(b).ok(),
            Message::Close(_) => break,
            _ => None,
        };
        if let Some(raw) = raw_opt {
            if let Ok(WireFrame::Encrypted {
                seq,
                ciphertext_b64,
            }) = serde_json::from_str::<WireFrame>(&raw)
            {
                if let Ok(Some(reply)) = hub.handle_encrypted_stream_frame(
                    &device_id,
                    TransportMedium::Web,
                    &cipher,
                    seq,
                    &ciphertext_b64,
                    &mut reply_seq,
                ) {
                    if let Ok(reply_json) = serde_json::to_string(&reply) {
                        let _ = ws.send(Message::Text(reply_json)).await;
                    }
                }
            }
        }
    }

    hub.close_session(&session_id);
    Ok(())
}
