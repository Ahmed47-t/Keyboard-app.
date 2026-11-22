use crate::protocol::TCP_CONTROL_PORT;
use crate::session::HostHub;
use crate::transports::{classify_ip_medium, run_stream_session};
use std::net::{Ipv4Addr, SocketAddrV4};
use std::sync::Arc;
use tokio::net::TcpListener;

pub async fn run_tcp_control_server(hub: Arc<HostHub>) {
    let addr = SocketAddrV4::new(Ipv4Addr::UNSPECIFIED, TCP_CONTROL_PORT);
    let Ok(listener) = TcpListener::bind(addr).await else {
        return;
    };

    loop {
        if let Ok((stream, peer_addr)) = listener.accept().await {
            let _ = stream.set_nodelay(true);
            let default_medium = classify_ip_medium(peer_addr.ip());
            let hub_clone = Arc::clone(&hub);
            tokio::spawn(async move {
                let _ = run_stream_session(stream, default_medium, hub_clone).await;
            });
        }
    }
}
