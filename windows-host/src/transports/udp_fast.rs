use crate::protocol::UDP_FAST_PORT;
use crate::session::HostHub;
use std::net::{Ipv4Addr, SocketAddrV4};
use std::sync::Arc;
use tokio::net::UdpSocket;

pub async fn run_udp_fast_server(hub: Arc<HostHub>) {
    let addr = SocketAddrV4::new(Ipv4Addr::UNSPECIFIED, UDP_FAST_PORT);
    let Ok(socket) = UdpSocket::bind(addr).await else {
        return;
    };

    let mut buf = [0u8; 2048];
    loop {
        if let Ok((n, _peer)) = socket.recv_from(&mut buf).await {
            hub.handle_fast_udp_datagram(&buf[..n]);
        }
    }
}
