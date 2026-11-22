# KeyConnect Wire Protocol v1 Specification

KeyConnect uses a lightweight, keyboard-specialized protocol inspired by **KDE Connect**, stripping away non-keyboard plugin bloat in favor of sub-millisecond encrypted keystroke transport and seamless multi-medium failover (**USB ⚡**, **Wi-Fi 📶**, **Bluetooth 🔵**).

---

## 1. Network Ports & Service Identifiers

| Medium / Channel | Port / Identifier | Transport | Purpose |
| :--- | :--- | :--- | :--- |
| **LAN / USB-RNDIS Discovery** | `UDP 45820` | IPv4 Broadcast / Unicast | Zero-config host discovery (`KC_DISCOVER_V1` / `KC_ANNOUNCE_V1`) |
| **Control, Pairing & Reliable Input** | `TCP 45821` | `TCP_NODELAY` Stream | Pairing handshake, text injection, reliable key events, ADB USB tunnel (`127.0.0.1:45821`) |
| **Fast-Path Keystrokes** | `UDP 45822` | Encrypted Datagrams | Zero head-of-line blocking keydown/keyup frames on Wi-Fi / USB-RNDIS |
| **Web Companion / Simulator** | `TCP 45823` | HTTP + WebSocket (`/ws`) | Browser fallback keyboard & live diagnostic stream |
| **Bluetooth Classic RFCOMM** | `8e7f1a20-4b9c-4d1e-9a6b-5c3d2e1f0a9b` | Winsock `AF_BTH` / Android `BluetoothSocket` | Offline wireless encrypted stream (identical framing to TCP `45821`) |
| **Bluetooth Hardware HID** | Standard HID UUID `00001124-0000-1000-8000-00805f9b34fb` | Android `BluetoothHidDevice` | Driverless BIOS / Lock-Screen hardware keyboard profile |

---

## 2. Cryptographic Identity & Pairing (KDE Connect Model)

1. **Persistent X25519 Identity**:
   - Both the Windows Host and Android Client generate a persistent 32-byte **X25519** keypair on first launch.
   - `device_id`: 16-char hex prefix of `SHA-256(public_key_bytes)`.

2. **Deterministic 6-Digit Pairing PIN**:
   - Computed identically by both peers from their static X25519 public keys:
     ```text
     sorted_keys = min(pub_a, pub_b) || max(pub_a, pub_b)
     digest      = SHA-256("KC-PIN-V1" || sorted_keys)
     pin_num     = u32_be(digest[0..4]) % 1_000_000
     pin_display = format!("{:03} {:03}", pin_num / 1000, pin_num % 1000)
     ```
   - Alternatively, scanning the Windows Host **QR Code** transmits the host's `public_key` + a 128-bit ephemeral `qr_pairing_token`, completing mutual authentication in a single round-trip without manual PIN comparison.

3. **Session Key Derivation (ECDH + HKDF-SHA256)**:
   - Upon connection over TCP, RFCOMM, or USB tunnel, peers exchange `HelloFrame` containing their static `public_key` and a random 16-byte `session_nonce`.
   - Shared secret: `raw_ecdh = X25519(my_static_secret, peer_static_public)`
   - Salt: `client_nonce_16B || host_nonce_16B` (32 bytes)
   - Info: `"KeyConnect-Session-v1"` (ASCII bytes)
   - Output Keying Material: `session_key = HKDF-SHA256(ikm = raw_ecdh, salt = salt, info = info, len = 32)`

4. **Authenticated Encryption (ChaCha20-Poly1305, RFC 8439)**:
   - Every post-handshake message is encrypted with **ChaCha20-Poly1305** (16-byte authentication tag).
   - 12-byte Nonce layout prevents nonce reuse and reflection attacks:
     - Client -> Host: `[0x43, 0x32, 0x48, 0x01]` (`"C2H\x01"`) + `u64_be(packet_seq)`
     - Host -> Client: `[0x48, 0x32, 0x43, 0x01]` (`"H2C\x01"`) + `u64_be(packet_seq)`
   - Associated Data (AAD): `session_id` (8 bytes).

---

## 3. Wire Framing

### Stream Transports (TCP `45821`, USB ADB `127.0.0.1:45821`, Bluetooth RFCOMM)
Each frame is prefixed with a 4-byte big-endian unsigned payload length (`max 65,536 bytes`):
```text
+----------------------+-----------------------------------------+
| length: u32 BE (4B)  | UTF-8 JSON Envelope (plaintext/cipher)  |
+----------------------+-----------------------------------------+
```

### UDP Fast-Path (`UDP 45822`)
Compact binary header + ChaCha20-Poly1305 ciphertext for zero head-of-line blocking:
```text
+------------------+------------------+--------------------------------------+
| session_id (8B)  | seq_num u64 (8B) | ChaCha20-Poly1305(ClientMessage JSON)|
+------------------+------------------+--------------------------------------+
```

---

## 4. Application Payloads (Encrypted `ClientMessage` / `HostMessage`)

Every `ClientMessage` carries a monotonically increasing `event_id: u64` per typing session. When the Android `ConnectionOrchestrator` seamlessly fails over between **USB**, **Wi-Fi**, and **Bluetooth**, in-flight unacknowledged events can be re-sent on the new link while the Windows `HostHub` deduplicates via a sliding bitset window.

### Client -> Host (`ClientMessage`)
- `KeyEvent`:
  - `event_id: u64`
  - `action`: `"down"` | `"up"` | `"press"`
  - `key`: Canonical key identifier (`"KeyA"`..`"KeyZ"`, `"Digit0"`..`"Digit9"`, `"Enter"`, `"Backspace"`, `"Tab"`, `"Space"`, `"Escape"`, `"ArrowUp"`, `"CtrlLeft"`, `"AltLeft"`, `"MetaLeft"`, `"ShiftLeft"`, `"F1"`..`"F12"`, etc.)
  - `modifiers`: `{ ctrl: bool, alt: bool, shift: bool, meta: bool }`
- `TextCommit`:
  - `event_id: u64`
  - `text: String` — Arbitrary UTF-8 text (Arabic letters `السلام عليكم`, emojis, swipe-typed words, voice dictation) injected directly via Win32 `KEYEVENTF_UNICODE` regardless of the active Windows keyboard layout.
- `ShortcutCombo`:
  - `event_id: u64`
  - `modifiers`: `["ctrl", "shift", ...]`
  - `key`: Canonical key identifier
- `ReleaseAll`: Immediately releases all held modifier/character keys on the host.
- `Ping`: `{ timestamp_ms: u64, medium: "usb" | "wifi" | "bluetooth" }`

### Host -> Client (`HostMessage`)
- `Pong`: `{ client_timestamp_ms: u64, active_medium: String }`
- `Ack`: `{ up_to_event_id: u64 }`
- `DisconnectNotice`: `{ reason: String }`
