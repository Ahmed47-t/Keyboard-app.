use crate::session::HostHub;
use std::sync::Arc;

pub fn spawn_bluetooth_rfcomm_server(hub: Arc<HostHub>) {
    #[cfg(windows)]
    {
        std::thread::Builder::new()
            .name("keyconnect-bt-rfcomm".to_string())
            .spawn(move || {
                windows_bt::run_blocking_rfcomm_listener(hub);
            })
            .ok();
    }

    #[cfg(not(windows))]
    {
        hub.set_bluetooth_status("مستقبل البلوتوث متاح عند التشغيل على نظام Windows".to_string());
    }
}

#[cfg(windows)]
mod windows_bt {
    use crate::protocol::{TransportMedium, WireFrame};
    use crate::session::HostHub;
    use std::mem::{size_of, zeroed};
    use std::ptr::{null, null_mut};
    use std::sync::Arc;
    use windows_sys::core::GUID;
    use windows_sys::Win32::Devices::Bluetooth::{AF_BTH, BTHPROTO_RFCOMM, SOCKADDR_BTH};
    use windows_sys::Win32::Networking::WinSock::{
        accept, bind, closesocket, getsockname, listen, recv, send, socket, WSASetServiceW,
        WSAStartup, CSADDR_INFO, INVALID_SOCKET, RNRSERVICE_REGISTER, SOCKADDR, SOCKET,
        SOCKET_ADDRESS, SOCKET_ERROR, WSADATA, WSAQUERYSETW,
    };

    const NS_BTH_CONST: u32 = 16;
    const BT_PORT_ANY_CONST: u32 = 0xFFFF_FFFF;
    const SOCK_STREAM_CONST: i32 = 1;

    /// UUID: 8e7f1a20-4b9c-4d1e-9a6b-5c3d2e1f0a9b
    const KEYCONNECT_BT_GUID: GUID = GUID {
        data1: 0x8e7f_1a20,
        data2: 0x4b9c,
        data3: 0x4d1e,
        data4: [0x9a, 0x6b, 0x5c, 0x3d, 0x2e, 0x1f, 0x0a, 0x9b],
    };

    pub fn run_blocking_rfcomm_listener(hub: Arc<HostHub>) {
        unsafe {
            let mut wsa_data: WSADATA = zeroed();
            if WSAStartup(0x0202, &mut wsa_data) != 0 {
                hub.set_bluetooth_status("تعذر تهيئة Winsock للبلوتوث".to_string());
                return;
            }

            let listen_sock = socket(
                AF_BTH as i32,
                SOCK_STREAM_CONST as _,
                BTHPROTO_RFCOMM as i32,
            );
            if listen_sock == INVALID_SOCKET {
                hub.set_bluetooth_status(
                    "البلوتوث غير مفعّل أو لا يوجد محول بلوتوث نشط".to_string(),
                );
                return;
            }

            let mut local_addr = SOCKADDR_BTH {
                addressFamily: AF_BTH,
                btAddr: 0,
                serviceClassId: zeroed(),
                port: BT_PORT_ANY_CONST,
            };

            if bind(
                listen_sock,
                (&local_addr as *const SOCKADDR_BTH).cast::<SOCKADDR>(),
                size_of::<SOCKADDR_BTH>() as i32,
            ) == SOCKET_ERROR
            {
                let _ = closesocket(listen_sock);
                hub.set_bluetooth_status(
                    "يرجى تفعيل البلوتوث في إعدادات ويندوز لاستقبال الاتصال".to_string(),
                );
                return;
            }

            let mut addr_len = size_of::<SOCKADDR_BTH>() as i32;
            if getsockname(
                listen_sock,
                (&mut local_addr as *mut SOCKADDR_BTH).cast::<SOCKADDR>(),
                &mut addr_len,
            ) == SOCKET_ERROR
            {
                let _ = closesocket(listen_sock);
                return;
            }

            if listen(listen_sock, 4) == SOCKET_ERROR {
                let _ = closesocket(listen_sock);
                return;
            }

            // Register SDP Service Record so Android BluetoothSocket connects via UUID automatically
            let mut service_guid = KEYCONNECT_BT_GUID;
            let mut service_name: Vec<u16> = "KeyConnect Remote Keyboard\0".encode_utf16().collect();
            let mut service_comment: Vec<u16> =
                "Encrypted Low-Latency Remote Keyboard\0".encode_utf16().collect();

            let mut cs_addr = CSADDR_INFO {
                LocalAddr: SOCKET_ADDRESS {
                    lpSockaddr: (&mut local_addr as *mut SOCKADDR_BTH).cast::<SOCKADDR>(),
                    iSockaddrLength: size_of::<SOCKADDR_BTH>() as i32,
                },
                RemoteAddr: SOCKET_ADDRESS {
                    lpSockaddr: null_mut(),
                    iSockaddrLength: 0,
                },
                iSocketType: SOCK_STREAM_CONST,
                iProtocol: BTHPROTO_RFCOMM as i32,
            };

            let mut query_set: WSAQUERYSETW = zeroed();
            query_set.dwSize = size_of::<WSAQUERYSETW>() as u32;
            query_set.lpszServiceInstanceName = service_name.as_mut_ptr();
            query_set.lpszComment = service_comment.as_mut_ptr();
            query_set.lpServiceClassId = &mut service_guid;
            query_set.dwNameSpace = NS_BTH_CONST;
            query_set.dwNumberOfCsAddrs = 1;
            query_set.lpcsaBuffer = &mut cs_addr;

            let _ = WSASetServiceW(&query_set, RNRSERVICE_REGISTER, 0);

            let rfcomm_port = local_addr.port;
            hub.set_bluetooth_status(format!(
                "مستقبل البلوتوث RFCOMM جاهز ونشط (قناة {})",
                rfcomm_port
            ));

            loop {
                let mut client_addr: SOCKADDR_BTH = zeroed();
                let mut client_len = size_of::<SOCKADDR_BTH>() as i32;
                let client_sock = accept(
                    listen_sock,
                    (&mut client_addr as *mut SOCKADDR_BTH).cast::<SOCKADDR>(),
                    &mut client_len,
                );
                if client_sock == INVALID_SOCKET {
                    std::thread::sleep(std::time::Duration::from_secs(2));
                    continue;
                }

                let hub_client = Arc::clone(&hub);
                std::thread::spawn(move || {
                    let _ = handle_rfcomm_client(client_sock, hub_client);
                    let _ = closesocket(client_sock);
                });
            }
        }
    }

    fn recv_exact_bt(sock: SOCKET, buf: &mut [u8]) -> Result<(), String> {
        let mut offset = 0usize;
        while offset < buf.len() {
            let n = unsafe {
                recv(
                    sock,
                    buf[offset..].as_mut_ptr(),
                    (buf.len() - offset) as i32,
                    0 as _,
                )
            };
            if n <= 0 {
                return Err("Bluetooth socket closed".to_string());
            }
            offset += n as usize;
        }
        Ok(())
    }

    fn send_all_bt(sock: SOCKET, buf: &[u8]) -> Result<(), String> {
        let mut offset = 0usize;
        while offset < buf.len() {
            let n = unsafe {
                send(
                    sock,
                    buf[offset..].as_ptr(),
                    (buf.len() - offset) as i32,
                    0 as _,
                )
            };
            if n <= 0 {
                return Err("Bluetooth send failed".to_string());
            }
            offset += n as usize;
        }
        Ok(())
    }

    fn read_bt_frame(sock: SOCKET) -> Result<WireFrame, String> {
        let mut len_buf = [0u8; 4];
        recv_exact_bt(sock, &mut len_buf)?;
        let len = u32::from_be_bytes(len_buf) as usize;
        if len == 0 || len > 65_536 {
            return Err("Invalid BT frame size".to_string());
        }
        let mut payload = vec![0u8; len];
        recv_exact_bt(sock, &mut payload)?;
        serde_json::from_slice(&payload).map_err(|e| e.to_string())
    }

    fn write_bt_frame(sock: SOCKET, frame: &WireFrame) -> Result<(), String> {
        let data = serde_json::to_vec(frame).map_err(|e| e.to_string())?;
        let len = (data.len() as u32).to_be_bytes();
        send_all_bt(sock, &len)?;
        send_all_bt(sock, &data)
    }

    fn handle_rfcomm_client(sock: SOCKET, hub: Arc<HostHub>) -> Result<(), String> {
        let hello = read_bt_frame(sock)?;
        let (device_id, device_name, public_key_hex, client_nonce_hex, qr_token) = match hello {
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
            _ => return Err("Expected ClientHello over Bluetooth".to_string()),
        };

        let (host_hello, cipher, approval_rx) = hub.begin_handshake(
            device_id.clone(),
            device_name.clone(),
            public_key_hex,
            client_nonce_hex,
            TransportMedium::Bluetooth,
            qr_token,
        )?;

        write_bt_frame(sock, &host_hello)?;

        if let Some(rx) = approval_rx {
            let accepted = rx.blocking_recv().unwrap_or(false);
            write_bt_frame(
                sock,
                &WireFrame::PairingDecision {
                    accepted,
                    reason: if accepted {
                        None
                    } else {
                        Some("تم رفض الإقران عبر البلوتوث".to_string())
                    },
                },
            )?;
            if !accepted {
                return Ok(());
            }
        }

        let session_id = cipher.session_id();
        hub.activate_session(
            device_id.clone(),
            device_name,
            TransportMedium::Bluetooth,
            cipher.clone(),
        );

        let mut reply_seq = 0u64;
        let res: Result<(), String> = (|| loop {
            let frame = read_bt_frame(sock)?;
            if let WireFrame::Encrypted {
                seq,
                ciphertext_b64,
            } = frame
            {
                if let Some(reply) = hub.handle_encrypted_stream_frame(
                    &device_id,
                    TransportMedium::Bluetooth,
                    &cipher,
                    seq,
                    &ciphertext_b64,
                    &mut reply_seq,
                )? {
                    write_bt_frame(sock, &reply)?;
                }
            }
        })();

        hub.close_session(&session_id);
        let _ = null::<u8>();
        res
    }
}
