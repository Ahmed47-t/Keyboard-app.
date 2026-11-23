#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

mod config;
mod crypto;
mod input_injector;
mod protocol;
mod session;
mod transports;
#[cfg(windows)]
mod ui;

use config::HostConfig;
use session::HostHub;
use std::sync::Arc;

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let args: Vec<String> = std::env::args().collect();
    let headless = args.iter().any(|a| a == "--headless");
    #[cfg(windows)]
    let start_minimized = args.iter().any(|a| a == "--minimized");

    let cfg = HostConfig::load_or_create();
    let hub = HostHub::new(cfg);

    let rt = tokio::runtime::Builder::new_multi_thread()
        .enable_all()
        .build()?;

    transports::spawn_all_transports(rt.handle(), Arc::clone(&hub));

    #[cfg(not(windows))]
    {
        let _ = headless;
        rt.block_on(async {
            std::future::pending::<()>().await;
        });
        Ok(())
    }

    #[cfg(windows)]
    {
        if headless {
            rt.block_on(async {
                std::future::pending::<()>().await;
            });
            return Ok(());
        }

        let mut viewport = eframe::egui::ViewportBuilder::default()
            .with_title("KeyConnect — Remote Keyboard Host")
            .with_inner_size([880.0, 560.0])
            .with_min_inner_size([720.0, 460.0]);

        if start_minimized {
            viewport = viewport.with_minimized(true);
        }

        let native_options = eframe::NativeOptions {
            viewport,
            ..Default::default()
        };

        let hub_for_ui = Arc::clone(&hub);
        eframe::run_native(
            "KeyConnect — Remote Keyboard Host",
            native_options,
            Box::new(move |cc| Ok(Box::new(ui::KeyConnectGui::new(cc, hub_for_ui)))),
        )?;

        Ok(())
    }
}
