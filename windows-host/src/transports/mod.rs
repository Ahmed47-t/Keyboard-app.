pub mod bluetooth_rfcomm;
pub mod discovery;
pub mod tcp_server;
pub mod udp_fast;
pub mod usb_adb;
pub mod web_companion;

use crate::protocol::{TransportMedium, WireFrame};
use crate::session::HostHub;
use std::net::IpAddr;
use std::sync::Arc;
use std::time::Duration;
use tokio::io::{AsyncRead, AsyncReadExt, AsyncWrite, AsyncWriteExt};

pub fn spawn_all_transports(rt: &tokio::runtime::Handle, hub: Arc<HostHub>) {
    rt.spawn(discovery::run_discovery_service(Arc::clone(&hub)));
    rt.spawn(tcp_server::run_tcp_control_server(Arc::clone(&hub)));
    rt.spawn(udp_fast::run_udp_fast_server(Arc::clone(&hub)));
    rt.spawn(usb_adb::run_usb_adb_watcher(Arc::clone(&hub)));
    rt.spawn(web_companion::run_web_companion_server(Arc::clone(&hub)));
    bluetooth_rfcomm::spawn_bluetooth_rfcomm_server(Arc::clone(&hub));
}

/// Classifies an incoming IPv4/IPv6 peer address into USB (ADB loopback or RNDIS USB Tethering) vs Wi-Fi.
pub fn classify_ip_medium(ip: IpAddr) -> TransportMedium {
    if ip.is_loopback() {
        // 127.0.0.1 comes from `adb reverse tcp:45821 tcp:45821` over physical USB cable!
        return TransportMedium::Usb;
    }
    if let IpAddr::V4(v4) = ip {
        let oct = v4.octets();
        // Android standard USB Tethering (RNDIS / NCM) subnets: 192.168.42.x, 192.168.44.x, 192.168.136.x
        if oct[0] == 192 && oct[1] == 168 && (oct[2] == 42 || oct[2] == 44 || oct[2] == 136) {
            return TransportMedium::Usb;
        }
    }
    TransportMedium::Wifi
}

pub async fn read_wire_frame<R: AsyncRead + Unpin>(reader: &mut R) -> Result<WireFrame, String> {
    let mut len_buf = [0u8; 4];
    reader
        .read_exact(&mut len_buf)
        .await
        .map_err(|e| format!("Stream closed: {e}"))?;
    let len = u32::from_be_bytes(len_buf) as usize;
    if len == 0 || len > 65_536 {
        return Err(format!("Invalid frame length: {len}"));
    }
    let mut payload = vec![0u8; len];
    reader
        .read_exact(&mut payload)
        .await
        .map_err(|e| format!("Failed reading payload: {e}"))?;
    serde_json::from_slice(&payload).map_err(|e| format!("Invalid WireFrame JSON: {e}"))
}

pub async fn write_wire_frame<W: AsyncWrite + Unpin>(
    writer: &mut W,
    frame: &WireFrame,
) -> Result<(), String> {
    let data = serde_json::to_vec(frame).map_err(|e| e.to_string())?;
    let len = (data.len() as u32).to_be_bytes();
    writer.write_all(&len).await.map_err(|e| e.to_string())?;
    writer.write_all(&data).await.map_err(|e| e.to_string())?;
    writer.flush().await.map_err(|e| e.to_string())
}

/// Runs a complete authenticated handshake + encrypted keystroke loop over any async byte stream.
pub async fn run_stream_session<S>(
    mut stream: S,
    default_medium: TransportMedium,
    hub: Arc<HostHub>,
) -> Result<(), String>
where
    S: AsyncRead + AsyncWrite + Unpin,
{
    let first_frame = tokio::time::timeout(Duration::from_secs(12), read_wire_frame(&mut stream))
        .await
        .map_err(|_| "Handshake timeout".to_string())??;

    let (device_id, device_name, public_key_hex, client_nonce_hex, reported_medium, qr_token) =
        match first_frame {
            WireFrame::ClientHello {
                device_id,
                device_name,
                public_key_hex,
                client_nonce_hex,
                medium,
                qr_pairing_token,
            } => (
                device_id,
                device_name,
                public_key_hex,
                client_nonce_hex,
                medium,
                qr_pairing_token,
            ),
            _ => return Err("Expected ClientHello".to_string()),
        };

    let effective_medium = if default_medium == TransportMedium::Usb {
        TransportMedium::Usb
    } else {
        reported_medium
    };

    let (host_hello, cipher, approval_rx) = hub.begin_handshake(
        device_id.clone(),
        device_name.clone(),
        public_key_hex,
        client_nonce_hex,
        effective_medium,
        qr_token,
    )?;

    write_wire_frame(&mut stream, &host_hello).await?;

    if let Some(rx) = approval_rx {
        let accepted = tokio::time::timeout(Duration::from_secs(90), rx)
            .await
            .map_err(|_| "Pairing confirmation timed out".to_string())?
            .unwrap_or(false);

        write_wire_frame(
            &mut stream,
            &WireFrame::PairingDecision {
                accepted,
                reason: if accepted {
                    None
                } else {
                    Some("تم رفض طلب الإقران من الحاسوب".to_string())
                },
            },
        )
        .await?;

        if !accepted {
            return Ok(());
        }
    }

    let session_id = cipher.session_id();
    hub.activate_session(
        device_id.clone(),
        device_name,
        effective_medium,
        cipher.clone(),
    );

    let mut reply_seq = 0u64;
    let loop_res: Result<(), String> = async {
        loop {
            let frame = read_wire_frame(&mut stream).await?;
            if let WireFrame::Encrypted {
                seq,
                ciphertext_b64,
            } = frame
            {
                if let Some(reply_frame) = hub.handle_encrypted_stream_frame(
                    &device_id,
                    effective_medium,
                    &cipher,
                    seq,
                    &ciphertext_b64,
                    &mut reply_seq,
                )? {
                    write_wire_frame(&mut stream, &reply_frame).await?;
                }
            }
        }
    }
    .await;

    hub.close_session(&session_id);
    loop_res
}
