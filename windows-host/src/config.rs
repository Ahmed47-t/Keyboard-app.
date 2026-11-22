use crate::crypto::{random_bytes, StaticIdentity};
use crate::protocol::TCP_CONTROL_PORT;
use serde::{Deserialize, Serialize};
use std::collections::BTreeMap;
use std::fs;
use std::path::PathBuf;
use std::time::{SystemTime, UNIX_EPOCH};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct TrustedDevice {
    pub device_id: String,
    pub device_name: String,
    pub public_key_hex: String,
    pub paired_at_unix: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HostConfig {
    pub host_name: String,
    pub secret_key_hex: String,
    pub auto_start: bool,
    pub start_minimized: bool,
    pub trusted_devices: BTreeMap<String, TrustedDevice>,
    #[serde(skip)]
    pub qr_pairing_token: String,
}

impl HostConfig {
    pub fn config_path() -> PathBuf {
        let base = dirs::config_dir().unwrap_or_else(|| PathBuf::from("."));
        base.join("KeyConnect").join("config.json")
    }

    pub fn load_or_create() -> Self {
        let path = Self::config_path();
        let qr_token = hex::encode(random_bytes::<16>());

        if let Ok(raw) = fs::read_to_string(&path) {
            if let Ok(mut cfg) = serde_json::from_str::<HostConfig>(&raw) {
                cfg.qr_pairing_token = qr_token;
                return cfg;
            }
        }

        let identity = StaticIdentity::generate();
        let host_name = std::env::var("COMPUTERNAME")
            .or_else(|_| std::env::var("HOSTNAME"))
            .unwrap_or_else(|_| "Windows-Laptop".to_string());

        let cfg = Self {
            host_name,
            secret_key_hex: hex::encode(identity.secret_bytes()),
            auto_start: false,
            start_minimized: false,
            trusted_devices: BTreeMap::new(),
            qr_pairing_token: qr_token,
        };
        let _ = cfg.save();
        cfg
    }

    pub fn save(&self) -> Result<(), String> {
        let path = Self::config_path();
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent).map_err(|e| e.to_string())?;
        }
        let json = serde_json::to_string_pretty(self).map_err(|e| e.to_string())?;
        fs::write(path, json).map_err(|e| e.to_string())
    }

    pub fn identity(&self) -> StaticIdentity {
        let decoded = hex::decode(&self.secret_key_hex).unwrap_or_default();
        if let Ok(arr) = <[u8; 32]>::try_from(decoded.as_slice()) {
            StaticIdentity::from_secret_bytes(arr)
        } else {
            StaticIdentity::generate()
        }
    }

    pub fn rotate_qr_token(&mut self) {
        self.qr_pairing_token = hex::encode(random_bytes::<16>());
    }

    pub fn is_trusted(&self, device_id: &str, public_key_hex: &str) -> bool {
        self.trusted_devices
            .get(device_id)
            .map(|d| d.public_key_hex.eq_ignore_ascii_case(public_key_hex))
            .unwrap_or(false)
    }

    pub fn add_trusted(&mut self, device_id: String, device_name: String, public_key_hex: String) {
        let now = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|d| d.as_secs())
            .unwrap_or(0);
        self.trusted_devices.insert(
            device_id.clone(),
            TrustedDevice {
                device_id,
                device_name,
                public_key_hex,
                paired_at_unix: now,
            },
        );
        let _ = self.save();
    }

    pub fn remove_trusted(&mut self, device_id: &str) {
        self.trusted_devices.remove(device_id);
        let _ = self.save();
    }

    pub fn build_qr_uri(&self, local_ips: &[String]) -> String {
        let id = self.identity().device_id();
        let pub_hex = self.identity().public_hex();
        let ips_joined = if local_ips.is_empty() {
            "127.0.0.1".to_string()
        } else {
            local_ips.join(",")
        };
        let encoded_name = url_encode_simple(&self.host_name);
        format!(
            "keyconnect://pair?id={}&name={}&ips={}&port={}&pub={}&token={}",
            id, encoded_name, ips_joined, TCP_CONTROL_PORT, pub_hex, self.qr_pairing_token
        )
    }

    pub fn set_windows_autostart(&mut self, enable: bool) -> Result<(), String> {
        self.auto_start = enable;
        let _ = self.save();

        #[cfg(windows)]
        {
            use winreg::enums::*;
            use winreg::RegKey;

            let hkcu = RegKey::predef(HKEY_CURRENT_USER);
            let (run_key, _) = hkcu
                .create_subkey("Software\\Microsoft\\Windows\\CurrentVersion\\Run")
                .map_err(|e| e.to_string())?;
            if enable {
                let exe_path = std::env::current_exe().map_err(|e| e.to_string())?;
                let cmd = format!("\"{}\" --minimized", exe_path.display());
                run_key
                    .set_value("KeyConnectHost", &cmd)
                    .map_err(|e| e.to_string())?;
            } else {
                let _ = run_key.delete_value("KeyConnectHost");
            }
        }
        Ok(())
    }
}

fn url_encode_simple(input: &str) -> String {
    let mut out = String::with_capacity(input.len());
    for b in input.bytes() {
        if b.is_ascii_alphanumeric() || b == b'-' || b == b'_' || b == b'.' {
            out.push(b as char);
        } else {
            out.push_str(&format!("%{:02X}", b));
        }
    }
    out
}
