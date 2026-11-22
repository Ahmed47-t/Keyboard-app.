use serde::{Deserialize, Serialize};

pub const PROTOCOL_VERSION: u32 = 1;
pub const UDP_DISCOVERY_PORT: u16 = 45820;
pub const TCP_CONTROL_PORT: u16 = 45821;
pub const UDP_FAST_PORT: u16 = 45822;
pub const WS_COMPANION_PORT: u16 = 45823;

/// Bluetooth Classic RFCOMM Service UUID: 8e7f1a20-4b9c-4d1e-9a6b-5c3d2e1f0a9b
pub const BT_SERVICE_UUID_STR: &str = "8e7f1a20-4b9c-4d1e-9a6b-5c3d2e1f0a9b";

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum TransportMedium {
    Usb,
    Wifi,
    Bluetooth,
    Web,
}

impl TransportMedium {
    pub fn label_ar(self) -> &'static str {
        match self {
            Self::Usb => "سلك USB فائق السرعة",
            Self::Wifi => "شبكة Wi-Fi المحلية",
            Self::Bluetooth => "بلوتوث مباشر",
            Self::Web => "متصفح الويب",
        }
    }

    pub fn badge(self) -> &'static str {
        match self {
            Self::Usb => "USB ⚡",
            Self::Wifi => "Wi-Fi 📶",
            Self::Bluetooth => "Bluetooth 🔵",
            Self::Web => "Web 🌐",
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "packet_type", rename_all = "SCREAMING_SNAKE_CASE")]
pub enum DiscoveryPacket {
    KcDiscoverV1 {
        device_id: String,
        device_name: String,
        protocol_version: u32,
        public_key_hex: String,
    },
    KcAnnounceV1 {
        device_id: String,
        device_name: String,
        protocol_version: u32,
        tcp_port: u16,
        udp_fast_port: u16,
        ws_port: u16,
        public_key_hex: String,
        is_paired: bool,
    },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "frame", rename_all = "snake_case")]
pub enum WireFrame {
    ClientHello {
        device_id: String,
        device_name: String,
        public_key_hex: String,
        client_nonce_hex: String,
        medium: TransportMedium,
        qr_pairing_token: Option<String>,
    },
    HostHello {
        device_id: String,
        device_name: String,
        public_key_hex: String,
        host_nonce_hex: String,
        session_id_hex: String,
        paired: bool,
        pairing_pin: Option<String>,
    },
    PairingDecision {
        accepted: bool,
        reason: Option<String>,
    },
    Encrypted {
        seq: u64,
        ciphertext_b64: String,
    },
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum KeyAction {
    Down,
    Up,
    Press,
}

#[derive(Debug, Default, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub struct ModifiersState {
    #[serde(default)]
    pub ctrl: bool,
    #[serde(default)]
    pub alt: bool,
    #[serde(default)]
    pub shift: bool,
    #[serde(default)]
    pub meta: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum ClientMessage {
    KeyEvent {
        event_id: u64,
        action: KeyAction,
        key: String,
        #[serde(default)]
        modifiers: ModifiersState,
    },
    TextCommit {
        event_id: u64,
        text: String,
    },
    ShortcutCombo {
        event_id: u64,
        modifiers: Vec<String>,
        key: String,
    },
    ReleaseAll {
        event_id: u64,
    },
    Ping {
        timestamp_ms: u64,
        medium: TransportMedium,
    },
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum HostMessage {
    Pong {
        client_timestamp_ms: u64,
        active_medium: TransportMedium,
    },
    Ack {
        up_to_event_id: u64,
    },
    DisconnectNotice {
        reason: String,
    },
}

/// Maps canonical cross-platform KeyConnect key identifiers to Win32 Virtual-Key codes (VK_*).
pub fn canonical_key_to_win32_vk(key: &str) -> Option<u16> {
    let vk: u16 = match key {
        // Letters A-Z
        "KeyA" | "a" | "A" => 0x41,
        "KeyB" | "b" | "B" => 0x42,
        "KeyC" | "c" | "C" => 0x43,
        "KeyD" | "d" | "D" => 0x44,
        "KeyE" | "e" | "E" => 0x45,
        "KeyF" | "f" | "F" => 0x46,
        "KeyG" | "g" | "G" => 0x47,
        "KeyH" | "h" | "H" => 0x48,
        "KeyI" | "i" | "I" => 0x49,
        "KeyJ" | "j" | "J" => 0x4A,
        "KeyK" | "k" | "K" => 0x4B,
        "KeyL" | "l" | "L" => 0x4C,
        "KeyM" | "m" | "M" => 0x4D,
        "KeyN" | "n" | "N" => 0x4E,
        "KeyO" | "o" | "O" => 0x4F,
        "KeyP" | "p" | "P" => 0x50,
        "KeyQ" | "q" | "Q" => 0x51,
        "KeyR" | "r" | "R" => 0x52,
        "KeyS" | "s" | "S" => 0x53,
        "KeyT" | "t" | "T" => 0x54,
        "KeyU" | "u" | "U" => 0x55,
        "KeyV" | "v" | "V" => 0x56,
        "KeyW" | "w" | "W" => 0x57,
        "KeyX" | "x" | "X" => 0x58,
        "KeyY" | "y" | "Y" => 0x59,
        "KeyZ" | "z" | "Z" => 0x5A,

        // Top-row Digits 0-9
        "Digit0" | "0" => 0x30,
        "Digit1" | "1" => 0x31,
        "Digit2" | "2" => 0x32,
        "Digit3" | "3" => 0x33,
        "Digit4" | "4" => 0x34,
        "Digit5" | "5" => 0x35,
        "Digit6" | "6" => 0x36,
        "Digit7" | "7" => 0x37,
        "Digit8" | "8" => 0x38,
        "Digit9" | "9" => 0x39,

        // Control & Editing Keys
        "Backspace" => 0x08,
        "Tab" => 0x09,
        "Enter" | "NumpadEnter" => 0x0D,
        "Escape" | "Esc" => 0x1B,
        "Space" | " " => 0x20,
        "CapsLock" => 0x14,
        "Delete" | "Del" => 0x2E,
        "Insert" | "Ins" => 0x2D,
        "PrintScreen" | "PrtSc" => 0x2C,

        // Navigation & Arrows
        "PageUp" | "PgUp" => 0x21,
        "PageDown" | "PgDn" => 0x22,
        "End" => 0x23,
        "Home" => 0x24,
        "ArrowLeft" | "Left" => 0x25,
        "ArrowUp" | "Up" => 0x26,
        "ArrowRight" | "Right" => 0x27,
        "ArrowDown" | "Down" => 0x28,

        // Modifiers
        "ShiftLeft" | "Shift" => 0xA0,   // VK_LSHIFT
        "ShiftRight" => 0xA1,            // VK_RSHIFT
        "CtrlLeft" | "ControlLeft" | "Ctrl" => 0xA2, // VK_LCONTROL
        "CtrlRight" | "ControlRight" => 0xA3,        // VK_RCONTROL
        "AltLeft" | "Alt" => 0xA4,       // VK_LMENU
        "AltRight" | "AltGr" => 0xA5,    // VK_RMENU
        "MetaLeft" | "Win" | "Super" => 0x5B, // VK_LWIN
        "MetaRight" => 0x5C,             // VK_RWIN
        "ContextMenu" => 0x5D,           // VK_APPS

        // Function Keys F1-F12
        "F1" => 0x70,
        "F2" => 0x71,
        "F3" => 0x72,
        "F4" => 0x73,
        "F5" => 0x74,
        "F6" => 0x75,
        "F7" => 0x76,
        "F8" => 0x77,
        "F9" => 0x78,
        "F10" => 0x79,
        "F11" => 0x7A,
        "F12" => 0x7B,

        // OEM Punctuation Keys (US Standard Scancode Positions)
        "Semicolon" | ";" => 0xBA,    // VK_OEM_1
        "Equal" | "=" => 0xBB,        // VK_OEM_PLUS
        "Comma" | "," => 0xBC,        // VK_OEM_COMMA
        "Minus" | "-" => 0xBD,        // VK_OEM_MINUS
        "Period" | "." => 0xBE,       // VK_OEM_PERIOD
        "Slash" | "/" => 0xBF,        // VK_OEM_2
        "Backquote" | "`" => 0xC0,    // VK_OEM_3
        "BracketLeft" | "[" => 0xDB,  // VK_OEM_4
        "Backslash" | "\\" => 0xDC,   // VK_OEM_5
        "BracketRight" | "]" => 0xDD, // VK_OEM_6
        "Quote" | "'" => 0xDE,        // VK_OEM_7

        // Media Controls
        "VolumeMute" => 0xAD,
        "VolumeDown" => 0xAE,
        "VolumeUp" => 0xAF,
        "MediaNextTrack" => 0xB0,
        "MediaPrevTrack" => 0xB1,
        "MediaPlayPause" => 0xB3,

        _ => return None,
    };
    Some(vk)
}

/// Extended Win32 virtual keys that require KEYEVENTF_EXTENDEDKEY when synthesizing scancodes.
pub fn is_extended_vk(vk: u16) -> bool {
    matches!(
        vk,
        0x21..=0x28 // PgUp, PgDn, End, Home, Left, Up, Right, Down
        | 0x2C..=0x2E // PrtSc, Insert, Delete
        | 0x5B..=0x5D // LWin, RWin, Apps
        | 0xA3        // RControl
        | 0xA5        // RAlt / AltGr
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_vk_mapping_and_extended_flags() {
        assert_eq!(canonical_key_to_win32_vk("KeyC"), Some(0x43));
        assert_eq!(canonical_key_to_win32_vk("CtrlLeft"), Some(0xA2));
        assert_eq!(canonical_key_to_win32_vk("ArrowRight"), Some(0x27));
        assert!(is_extended_vk(0x27));
        assert!(is_extended_vk(0x5B));
        assert!(!is_extended_vk(0x43));
    }

    #[test]
    fn test_client_message_roundtrip() {
        let msg = ClientMessage::TextCommit {
            event_id: 42,
            text: "مرحباً من الهاتف".to_string(),
        };
        let json = serde_json::to_string(&msg).unwrap();
        let parsed: ClientMessage = serde_json::from_str(&json).unwrap();
        match parsed {
            ClientMessage::TextCommit { event_id, text } => {
                assert_eq!(event_id, 42);
                assert_eq!(text, "مرحباً من الهاتف");
            }
            _ => panic!("unexpected variant"),
        }
    }
}
