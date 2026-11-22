use crate::config::{HostConfig, TrustedDevice};
use crate::crypto::{
    compute_pairing_pin, derive_session_key, random_bytes, Direction, SessionCipher,
    SlidingDeduplicator, StaticIdentity,
};
use crate::input_injector::KeyboardInjector;
use crate::protocol::{
    ClientMessage, HostMessage, TransportMedium, WireFrame,
};
use base64::{engine::general_purpose::STANDARD as B64, Engine};
use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};
use tokio::sync::oneshot;

pub struct PendingPairing {
    pub device_id: String,
    pub device_name: String,
    pub public_key_hex: String,
    pub pin: String,
    pub medium: TransportMedium,
    pub decision_tx: Option<oneshot::Sender<bool>>,
}

#[derive(Clone)]
pub struct ActiveDeviceStatus {
    pub device_id: String,
    pub device_name: String,
    pub active_medium: TransportMedium,
    pub connected_since: Instant,
    pub last_activity: Instant,
    pub keystrokes_count: u64,
    pub last_key_preview: String,
}

#[derive(Clone)]
pub struct HubSnapshot {
    pub host_id: String,
    pub host_name: String,
    pub qr_uri: String,
    pub local_ips: Vec<String>,
    pub trusted_devices: Vec<TrustedDevice>,
    pub active_device: Option<ActiveDeviceStatus>,
    pub pending_pairing_info: Option<(String, String, String, TransportMedium)>, // (id, name, pin, medium)
    pub usb_adb_status: String,
    pub bluetooth_status: String,
    pub auto_start: bool,
}

struct HubInner {
    config: HostConfig,
    local_ips: Vec<String>,
    usb_adb_status: String,
    bluetooth_status: String,
    pending_pairing: Option<PendingPairing>,
    active_device: Option<ActiveDeviceStatus>,
    sessions_by_id: HashMap<[u8; 8], (String, TransportMedium, SessionCipher)>,
    dedup_by_device: HashMap<String, SlidingDeduplicator>,
}

pub struct HostHub {
    inner: Mutex<HubInner>,
    injector: KeyboardInjector,
    gui_waker: Mutex<Option<eframe::egui::Context>>,
}

impl HostHub {
    pub fn new(config: HostConfig) -> Arc<Self> {
        Arc::new(Self {
            inner: Mutex::new(HubInner {
                config,
                local_ips: Vec::new(),
                usb_adb_status: "جسر USB نشط — بانتظار توصيل الهاتف".to_string(),
                bluetooth_status: "جاري تهيئة مستقبل البلوتوث...".to_string(),
                pending_pairing: None,
                active_device: None,
                sessions_by_id: HashMap::new(),
                dedup_by_device: HashMap::new(),
            }),
            injector: KeyboardInjector::new(),
            gui_waker: Mutex::new(None),
        })
    }

    pub fn set_gui_context(&self, ctx: eframe::egui::Context) {
        if let Ok(mut guard) = self.gui_waker.lock() {
            *guard = Some(ctx);
        }
    }

    fn wake_gui(&self) {
        if let Ok(guard) = self.gui_waker.lock() {
            if let Some(ctx) = guard.as_ref() {
                ctx.request_repaint();
            }
        }
    }

    pub fn identity(&self) -> StaticIdentity {
        let guard = self.inner.lock().unwrap_or_else(|e| e.into_inner());
        guard.config.identity()
    }

    pub fn host_name(&self) -> String {
        let guard = self.inner.lock().unwrap_or_else(|e| e.into_inner());
        guard.config.host_name.clone()
    }

    pub fn is_trusted_id(&self, device_id: &str) -> bool {
        let guard = self.inner.lock().unwrap_or_else(|e| e.into_inner());
        guard.config.trusted_devices.contains_key(device_id)
    }

    pub fn update_local_ips(&self, ips: Vec<String>) {
        if let Ok(mut guard) = self.inner.lock() {
            if guard.local_ips != ips {
                guard.local_ips = ips;
                drop(guard);
                self.wake_gui();
            }
        }
    }

    pub fn set_usb_adb_status(&self, status: String) {
        if let Ok(mut guard) = self.inner.lock() {
            if guard.usb_adb_status != status {
                guard.usb_adb_status = status;
                drop(guard);
                self.wake_gui();
            }
        }
    }

    pub fn set_bluetooth_status(&self, status: String) {
        if let Ok(mut guard) = self.inner.lock() {
            guard.bluetooth_status = status;
        }
        self.wake_gui();
    }

    pub fn snapshot(&self) -> HubSnapshot {
        let guard = self.inner.lock().unwrap_or_else(|e| e.into_inner());
        let id = guard.config.identity().device_id();
        let qr_uri = guard.config.build_qr_uri(&guard.local_ips);
        let trusted: Vec<TrustedDevice> = guard.config.trusted_devices.values().cloned().collect();
        let pending_info = guard.pending_pairing.as_ref().map(|p| {
            (
                p.device_id.clone(),
                p.device_name.clone(),
                p.pin.clone(),
                p.medium,
            )
        });

        HubSnapshot {
            host_id: id,
            host_name: guard.config.host_name.clone(),
            qr_uri,
            local_ips: guard.local_ips.clone(),
            trusted_devices: trusted,
            active_device: guard.active_device.clone(),
            pending_pairing_info: pending_info,
            usb_adb_status: guard.usb_adb_status.clone(),
            bluetooth_status: guard.bluetooth_status.clone(),
            auto_start: guard.config.auto_start,
        }
    }

    pub fn resolve_pending_pairing(&self, accept: bool) {
        let mut guard = self.inner.lock().unwrap_or_else(|e| e.into_inner());
        if let Some(mut pending) = guard.pending_pairing.take() {
            if accept {
                guard.config.add_trusted(
                    pending.device_id.clone(),
                    pending.device_name.clone(),
                    pending.public_key_hex.clone(),
                );
            }
            if let Some(tx) = pending.decision_tx.take() {
                let _ = tx.send(accept);
            }
        }
        drop(guard);
        self.wake_gui();
    }

    pub fn unpair_device(&self, device_id: &str) {
        let mut guard = self.inner.lock().unwrap_or_else(|e| e.into_inner());
        guard.config.remove_trusted(device_id);
        if guard
            .active_device
            .as_ref()
            .map(|a| a.device_id == device_id)
            .unwrap_or(false)
        {
            guard.active_device = None;
            self.injector.release_all_held_keys();
        }
        drop(guard);
        self.wake_gui();
    }

    pub fn set_auto_start(&self, enabled: bool) -> Result<(), String> {
        let mut guard = self.inner.lock().unwrap_or_else(|e| e.into_inner());
        guard.config.set_windows_autostart(enabled)
    }

    pub fn rotate_qr_token(&self) {
        if let Ok(mut guard) = self.inner.lock() {
            guard.config.rotate_qr_token();
        }
        self.wake_gui();
    }

    /// Processes a `ClientHello` frame and returns `(HostHello, SessionCipher, needs_approval_rx)`.
    pub fn begin_handshake(
        &self,
        device_id: String,
        device_name: String,
        public_key_hex: String,
        client_nonce_hex: String,
        medium: TransportMedium,
        qr_pairing_token: Option<String>,
    ) -> Result<(WireFrame, SessionCipher, Option<oneshot::Receiver<bool>>), String> {
        let peer_pub: [u8; 32] = hex::decode(&public_key_hex)
            .map_err(|_| "Invalid client public_key_hex")?
            .try_into()
            .map_err(|_| "Client public key must be 32 bytes")?;
        let client_nonce: [u8; 16] = hex::decode(&client_nonce_hex)
            .map_err(|_| "Invalid client_nonce_hex")?
            .try_into()
            .map_err(|_| "Client nonce must be 16 bytes")?;

        let mut guard = self.inner.lock().unwrap_or_else(|e| e.into_inner());
        let host_identity = guard.config.identity();
        let host_nonce = random_bytes::<16>();
        let session_id = random_bytes::<8>();

        let shared_secret = host_identity.diffie_hellman(&peer_pub);
        let session_key = derive_session_key(&shared_secret, &client_nonce, &host_nonce);
        let cipher = SessionCipher::new(&session_key, session_id);

        let qr_valid = qr_pairing_token
            .as_ref()
            .map(|tok| !tok.is_empty() && tok == &guard.config.qr_pairing_token)
            .unwrap_or(false);

        if qr_valid {
            guard.config.add_trusted(
                device_id.clone(),
                device_name.clone(),
                public_key_hex.clone(),
            );
            guard.config.rotate_qr_token();
        }

        let already_trusted = guard.config.is_trusted(&device_id, &public_key_hex);
        let pin = compute_pairing_pin(&peer_pub, &host_identity.public_bytes());

        let approval_rx = if already_trusted {
            None
        } else {
            let (tx, rx) = oneshot::channel();
            guard.pending_pairing = Some(PendingPairing {
                device_id: device_id.clone(),
                device_name: device_name.clone(),
                public_key_hex,
                pin: pin.clone(),
                medium,
                decision_tx: Some(tx),
            });
            Some(rx)
        };

        let host_hello = WireFrame::HostHello {
            device_id: host_identity.device_id(),
            device_name: guard.config.host_name.clone(),
            public_key_hex: host_identity.public_hex(),
            host_nonce_hex: hex::encode(host_nonce),
            session_id_hex: hex::encode(session_id),
            paired: already_trusted,
            pairing_pin: if already_trusted { None } else { Some(pin) },
        };

        drop(guard);
        self.wake_gui();
        Ok((host_hello, cipher, approval_rx))
    }

    pub fn activate_session(
        &self,
        device_id: String,
        device_name: String,
        medium: TransportMedium,
        cipher: SessionCipher,
    ) {
        let mut guard = self.inner.lock().unwrap_or_else(|e| e.into_inner());
        guard.sessions_by_id.insert(
            cipher.session_id(),
            (device_id.clone(), medium, cipher),
        );
        guard.dedup_by_device.entry(device_id.clone()).or_default();

        let now = Instant::now();
        match &mut guard.active_device {
            Some(active) if active.device_id == device_id => {
                active.active_medium = medium;
                active.last_activity = now;
            }
            _ => {
                guard.active_device = Some(ActiveDeviceStatus {
                    device_id,
                    device_name,
                    active_medium: medium,
                    connected_since: now,
                    last_activity: now,
                    keystrokes_count: 0,
                    last_key_preview: String::new(),
                });
            }
        }
        drop(guard);
        self.wake_gui();
    }

    pub fn close_session(&self, session_id: &[u8; 8]) {
        let mut guard = self.inner.lock().unwrap_or_else(|e| e.into_inner());
        let removed = guard.sessions_by_id.remove(session_id);
        if let Some((dev_id, _, _)) = removed {
            let still_has_other_medium = guard
                .sessions_by_id
                .values()
                .any(|(other_dev, _, _)| other_dev == &dev_id);

            if !still_has_other_medium {
                if guard
                    .active_device
                    .as_ref()
                    .map(|a| a.device_id == dev_id)
                    .unwrap_or(false)
                {
                    guard.active_device = None;
                }
                self.injector.release_all_held_keys();
            } else if let Some((_, fallback_medium, _)) = guard
                .sessions_by_id
                .values()
                .find(|(other_dev, _, _)| other_dev == &dev_id)
                .cloned()
            {
                if let Some(active) = &mut guard.active_device {
                    active.active_medium = fallback_medium;
                }
            }
        }
        drop(guard);
        self.wake_gui();
    }

    /// Decrypts and executes a stream-framed `WireFrame::Encrypted` packet.
    pub fn handle_encrypted_stream_frame(
        &self,
        device_id: &str,
        medium: TransportMedium,
        cipher: &SessionCipher,
        seq: u64,
        ciphertext_b64: &str,
        reply_seq: &mut u64,
    ) -> Result<Option<WireFrame>, String> {
        let ct = B64
            .decode(ciphertext_b64)
            .map_err(|_| "Invalid base64 ciphertext".to_string())?;
        let plain = cipher.open(Direction::ClientToHost, seq, &ct)?;
        let msg: ClientMessage =
            serde_json::from_slice(&plain).map_err(|e| format!("Invalid ClientMessage: {e}"))?;

        if let Some(resp) = self.dispatch_client_message(device_id, medium, msg) {
            *reply_seq += 1;
            let resp_bytes = serde_json::to_vec(&resp).map_err(|e| e.to_string())?;
            let sealed = cipher.seal(Direction::HostToClient, *reply_seq, &resp_bytes)?;
            return Ok(Some(WireFrame::Encrypted {
                seq: *reply_seq,
                ciphertext_b64: B64.encode(sealed),
            }));
        }
        Ok(None)
    }

    /// Decrypts and executes a fast-path UDP datagram (`[8B session_id][8B seq][ciphertext]`).
    pub fn handle_fast_udp_datagram(&self, datagram: &[u8]) {
        if datagram.len() < 32 {
            return;
        }
        let Ok(session_id) = <[u8; 8]>::try_from(&datagram[..8]) else {
            return;
        };
        let Ok(seq_bytes) = <[u8; 8]>::try_from(&datagram[8..16]) else {
            return;
        };
        let seq = u64::from_be_bytes(seq_bytes);
        let ciphertext = &datagram[16..];

        let session_entry = {
            let guard = self.inner.lock().unwrap_or_else(|e| e.into_inner());
            guard.sessions_by_id.get(&session_id).cloned()
        };

        if let Some((device_id, medium, cipher)) = session_entry {
            if let Ok(plain) = cipher.open(Direction::ClientToHost, seq, ciphertext) {
                if let Ok(msg) = serde_json::from_slice::<ClientMessage>(&plain) {
                    let _ = self.dispatch_client_message(&device_id, medium, msg);
                }
            }
        }
    }

    pub fn dispatch_client_message(
        &self,
        device_id: &str,
        medium: TransportMedium,
        msg: ClientMessage,
    ) -> Option<HostMessage> {
        match msg {
            ClientMessage::Ping { timestamp_ms, .. } => {
                if let Ok(mut guard) = self.inner.lock() {
                    if let Some(active) = &mut guard.active_device {
                        if active.device_id == device_id
                            && active.last_activity.elapsed() > Duration::from_millis(800)
                        {
                            active.active_medium = medium;
                        }
                    }
                }
                Some(HostMessage::Pong {
                    client_timestamp_ms: timestamp_ms,
                    active_medium: medium,
                })
            }
            ClientMessage::KeyEvent {
                event_id,
                action,
                key,
                modifiers,
            } => {
                if !self.accept_event(device_id, medium, event_id, &key) {
                    return Some(HostMessage::Ack {
                        up_to_event_id: event_id,
                    });
                }
                self.injector.inject_key_event(action, &key, modifiers);
                Some(HostMessage::Ack {
                    up_to_event_id: event_id,
                })
            }
            ClientMessage::TextCommit { event_id, text } => {
                let preview = text.chars().take(6).collect::<String>();
                if !self.accept_event(device_id, medium, event_id, &preview) {
                    return Some(HostMessage::Ack {
                        up_to_event_id: event_id,
                    });
                }
                self.injector.inject_unicode_text(&text);
                Some(HostMessage::Ack {
                    up_to_event_id: event_id,
                })
            }
            ClientMessage::ShortcutCombo {
                event_id,
                modifiers,
                key,
            } => {
                let label = format!("{}+{}", modifiers.join("+"), key);
                if !self.accept_event(device_id, medium, event_id, &label) {
                    return Some(HostMessage::Ack {
                        up_to_event_id: event_id,
                    });
                }
                self.injector.inject_shortcut_combo(&modifiers, &key);
                Some(HostMessage::Ack {
                    up_to_event_id: event_id,
                })
            }
            ClientMessage::ReleaseAll { event_id } => {
                let _ = self.accept_event(device_id, medium, event_id, "ReleaseAll");
                self.injector.release_all_held_keys();
                Some(HostMessage::Ack {
                    up_to_event_id: event_id,
                })
            }
        }
    }

    fn accept_event(
        &self,
        device_id: &str,
        medium: TransportMedium,
        event_id: u64,
        preview: &str,
    ) -> bool {
        let mut guard = self.inner.lock().unwrap_or_else(|e| e.into_inner());
        let accepted = guard
            .dedup_by_device
            .entry(device_id.to_string())
            .or_default()
            .accept(event_id);

        if accepted {
            if let Some(active) = &mut guard.active_device {
                if active.device_id == device_id {
                    active.active_medium = medium;
                    active.last_activity = Instant::now();
                    active.keystrokes_count = active.keystrokes_count.saturating_add(1);
                    active.last_key_preview = preview.to_string();
                }
            }
            drop(guard);
            self.wake_gui();
        }
        accepted
    }
}
