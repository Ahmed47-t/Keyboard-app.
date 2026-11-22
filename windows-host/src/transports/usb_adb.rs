use crate::protocol::TCP_CONTROL_PORT;
use crate::session::HostHub;
use std::collections::HashSet;
use std::path::PathBuf;
use std::sync::Arc;
use std::time::Duration;
use tokio::process::Command;

pub async fn run_usb_adb_watcher(hub: Arc<HostHub>) {
    let mut forwarded_serials: HashSet<String> = HashSet::new();
    let mut tick = tokio::time::interval(Duration::from_secs(3));

    let adb_bin = locate_adb_binary();

    loop {
        tick.tick().await;

        let mut cmd = Command::new(&adb_bin);
        cmd.arg("devices");
        #[cfg(windows)]
        cmd.creation_flags(0x08000000); // CREATE_NO_WINDOW

        match cmd.output().await {
            Ok(out) if out.status.success() => {
                let stdout = String::from_utf8_lossy(&out.stdout);
                let mut current_usb_serials = Vec::new();

                for line in stdout.lines().skip(1) {
                    let parts: Vec<&str> = line.split_whitespace().collect();
                    if parts.len() >= 2 && parts[1] == "device" {
                        // Exclude wireless ADB (ip:port) so only physical USB cables count as USB ADB
                        if !parts[0].contains(':') {
                            current_usb_serials.push(parts[0].to_string());
                        }
                    }
                }

                forwarded_serials.retain(|s| current_usb_serials.contains(s));

                if current_usb_serials.is_empty() {
                    hub.set_usb_adb_status(
                        "جاهز لاتصال USB (وصّل السلك أو فعّل تقييد USB)".to_string(),
                    );
                } else {
                    for serial in &current_usb_serials {
                        if !forwarded_serials.contains(serial) {
                            let port_spec = format!("tcp:{TCP_CONTROL_PORT}");
                            let mut rev = Command::new(&adb_bin);
                            rev.args(["-s", serial, "reverse", &port_spec, &port_spec]);
                            #[cfg(windows)]
                            rev.creation_flags(0x08000000);

                            if let Ok(r_out) = rev.output().await {
                                if r_out.status.success() {
                                    forwarded_serials.insert(serial.clone());
                                }
                            }
                        }
                    }
                    hub.set_usb_adb_status(format!(
                        "نفق USB نشط للأجهزة: {}",
                        current_usb_serials.join(", ")
                    ));
                }
            }
            _ => {
                hub.set_usb_adb_status(
                    "تقييد USB (RNDIS) نشط تلقائياً • أداة ADB اختيارية".to_string(),
                );
            }
        }
    }
}

fn locate_adb_binary() -> PathBuf {
    if let Ok(exe) = std::env::current_exe() {
        if let Some(dir) = exe.parent() {
            let bundled = dir.join("adb.exe");
            if bundled.exists() {
                return bundled;
            }
        }
    }

    if let Ok(local_app_data) = std::env::var("LOCALAPPDATA") {
        let sdk_adb = PathBuf::from(local_app_data)
            .join("Android")
            .join("Sdk")
            .join("platform-tools")
            .join("adb.exe");
        if sdk_adb.exists() {
            return sdk_adb;
        }
    }

    PathBuf::from("adb")
}
