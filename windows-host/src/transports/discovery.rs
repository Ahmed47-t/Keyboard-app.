use crate::protocol::{
    DiscoveryPacket, PROTOCOL_VERSION, TCP_CONTROL_PORT, UDP_DISCOVERY_PORT, UDP_FAST_PORT,
    WS_COMPANION_PORT,
};
use crate::session::HostHub;
use std::net::{Ipv4Addr, SocketAddr, SocketAddrV4};
use std::sync::Arc;
use std::time::Duration;
use tokio::net::UdpSocket;

pub async fn run_discovery_service(hub: Arc<HostHub>) {
    let bind_addr = SocketAddrV4::new(Ipv4Addr::UNSPECIFIED, UDP_DISCOVERY_PORT);
    let Ok(socket) = UdpSocket::bind(bind_addr).await else {
        return;
    };
    let _ = socket.set_broadcast(true);

    let mut recv_buf = [0u8; 2048];
    let mut beacon_tick = tokio::time::interval(Duration::from_millis(2500));

    loop {
        tokio::select! {
            _ = beacon_tick.tick() => {
                let ips = detect_local_ipv4s().await;
                hub.update_local_ips(ips.clone());

                let identity = hub.identity();
                let announce = DiscoveryPacket::KcAnnounceV1 {
                    device_id: identity.device_id(),
                    device_name: hub.host_name(),
                    protocol_version: PROTOCOL_VERSION,
                    tcp_port: TCP_CONTROL_PORT,
                    udp_fast_port: UDP_FAST_PORT,
                    ws_port: WS_COMPANION_PORT,
                    public_key_hex: identity.public_hex(),
                    is_paired: false,
                };

                if let Ok(bytes) = serde_json::to_vec(&announce) {
                    // Global broadcast + subnet-directed broadcasts (covers Wi-Fi + USB RNDIS tethering)
                    let global_bcast = SocketAddr::V4(SocketAddrV4::new(
                        Ipv4Addr::BROADCAST,
                        UDP_DISCOVERY_PORT,
                    ));
                    let _ = socket.send_to(&bytes, global_bcast).await;

                    for ip_str in &ips {
                        if let Ok(ip) = ip_str.parse::<Ipv4Addr>() {
                            let oct = ip.octets();
                            let subnet_bcast = SocketAddr::V4(SocketAddrV4::new(
                                Ipv4Addr::new(oct[0], oct[1], oct[2], 255),
                                UDP_DISCOVERY_PORT,
                            ));
                            let _ = socket.send_to(&bytes, subnet_bcast).await;
                        }
                    }
                }
            }
            recv_res = socket.recv_from(&mut recv_buf) => {
                if let Ok((n, peer_addr)) = recv_res {
                    if let Ok(DiscoveryPacket::KcDiscoverV1 { device_id, .. }) =
                        serde_json::from_slice::<DiscoveryPacket>(&recv_buf[..n])
                    {
                        let identity = hub.identity();
                        let reply = DiscoveryPacket::KcAnnounceV1 {
                            device_id: identity.device_id(),
                            device_name: hub.host_name(),
                            protocol_version: PROTOCOL_VERSION,
                            tcp_port: TCP_CONTROL_PORT,
                            udp_fast_port: UDP_FAST_PORT,
                            ws_port: WS_COMPANION_PORT,
                            public_key_hex: identity.public_hex(),
                            is_paired: hub.is_trusted_id(&device_id),
                        };
                        if let Ok(bytes) = serde_json::to_vec(&reply) {
                            let _ = socket.send_to(&bytes, peer_addr).await;
                        }
                    }
                }
            }
        }
    }
}

/// Discovers active IPv4 addresses on Windows/Linux (including Wi-Fi LAN, Hotspot, and USB RNDIS).
async fn detect_local_ipv4s() -> Vec<String> {
    let mut found: Vec<String> = Vec::new();

    // Primary outbound route probe
    if let Ok(sock) = std::net::UdpSocket::bind("0.0.0.0:0") {
        if sock.connect("8.8.8.8:80").is_ok() {
            if let Ok(SocketAddr::V4(local)) = sock.local_addr() {
                let ip = local.ip();
                if !ip.is_loopback() && !ip.is_unspecified() {
                    found.push(ip.to_string());
                }
            }
        }
    }

    // Enumerate additional interfaces (e.g., USB RNDIS 192.168.42.x or Hotspot 192.168.137.1) via ipconfig on Windows
    #[cfg(windows)]
    {
        use tokio::process::Command;
        let mut cmd = Command::new("ipconfig");
        cmd.creation_flags(0x08000000); // CREATE_NO_WINDOW
        if let Ok(out) = cmd.output().await {
            let text = String::from_utf8_lossy(&out.stdout);
            for line in text.lines() {
                if line.contains("IPv4") {
                    if let Some(after_colon) = line.split(':').nth(1) {
                        let candidate = after_colon
                            .trim()
                            .trim_end_matches("(Preferred)")
                            .trim();
                        if let Ok(v4) = candidate.parse::<Ipv4Addr>() {
                            if !v4.is_loopback() && !v4.is_link_local() && !v4.is_unspecified() {
                                let s = v4.to_string();
                                if !found.contains(&s) {
                                    found.push(s);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    found
}
