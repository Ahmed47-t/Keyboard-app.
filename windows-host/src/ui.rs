use crate::protocol::{TCP_CONTROL_PORT, TransportMedium, UDP_FAST_PORT};
use crate::session::HostHub;
use eframe::egui::{
    self, Color32, FontData, FontDefinitions, FontFamily, Pos2, Rect, RichText, Rounding, Sense,
    Stroke, Vec2,
};
use qrcode::{Color as QrColor, QrCode};
use std::fs;
use std::sync::Arc;
use std::time::Duration;

pub struct KeyConnectGui {
    hub: Arc<HostHub>,
    cached_qr_uri: String,
    cached_qr_matrix: Option<(usize, Vec<bool>)>,
    status_toast: Option<String>,
}

impl KeyConnectGui {
    pub fn new(cc: &eframe::CreationContext<'_>, hub: Arc<HostHub>) -> Self {
        configure_fonts_and_theme(&cc.egui_ctx);
        hub.set_gui_context(cc.egui_ctx.clone());

        Self {
            hub,
            cached_qr_uri: String::new(),
            cached_qr_matrix: None,
            status_toast: None,
        }
    }

    fn ensure_qr_cached(&mut self, uri: &str) {
        if self.cached_qr_uri == uri && self.cached_qr_matrix.is_some() {
            return;
        }
        self.cached_qr_uri = uri.to_string();
        if let Ok(code) = QrCode::new(uri.as_bytes()) {
            let width = code.width();
            let modules: Vec<bool> = code
                .to_colors()
                .into_iter()
                .map(|c| matches!(c, QrColor::Dark))
                .collect();
            self.cached_qr_matrix = Some((width, modules));
        }
    }
}

impl eframe::App for KeyConnectGui {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        ctx.request_repaint_after(Duration::from_secs(1));
        let snap = self.hub.snapshot();
        self.ensure_qr_cached(&snap.qr_uri);

        // Top Header Bar
        egui::TopBottomPanel::top("top_header")
            .exact_height(62.0)
            .frame(
                egui::Frame::none()
                    .fill(Color32::from_rgb(15, 23, 42))
                    .inner_margin(egui::Margin::symmetric(20.0, 12.0)),
            )
            .show(ctx, |ui| {
                ui.horizontal(|ui| {
                    ui.vertical(|ui| {
                        ui.label(
                            RichText::new("KeyConnect Host")
                                .size(20.0)
                                .strong()
                                .color(Color32::from_rgb(56, 189, 248)),
                        );
                        ui.label(
                            RichText::new(format!(
                                "PC: {}  •  ID: {}",
                                snap.host_name, snap.host_id
                            ))
                            .size(12.0)
                            .color(Color32::from_rgb(148, 163, 184)),
                        );
                    });

                    ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                        if let Some(active) = &snap.active_device {
                            let badge_color = match active.active_medium {
                                TransportMedium::Usb => Color32::from_rgb(16, 185, 129),
                                TransportMedium::Wifi => Color32::from_rgb(14, 165, 233),
                                TransportMedium::Bluetooth => Color32::from_rgb(99, 102, 241),
                                TransportMedium::Web => Color32::from_rgb(245, 158, 11),
                            };
                            egui::Frame::none()
                                .fill(badge_color.gamma_multiply(0.22))
                                .stroke(Stroke::new(1.0, badge_color))
                                .rounding(Rounding::same(8.0))
                                .inner_margin(egui::Margin::symmetric(12.0, 6.0))
                                .show(ui, |ui| {
                                    ui.label(
                                        RichText::new(format!(
                                            "CONNECTED: {} ({}) • {} keys",
                                            active.device_name,
                                            active.active_medium.badge(),
                                            active.keystrokes_count
                                        ))
                                        .strong()
                                        .color(Color32::WHITE),
                                    );
                                });
                        } else {
                            egui::Frame::none()
                                .fill(Color32::from_rgb(30, 41, 59))
                                .rounding(Rounding::same(8.0))
                                .inner_margin(egui::Margin::symmetric(12.0, 6.0))
                                .show(ui, |ui| {
                                    ui.label(
                                        RichText::new("Ready — Waiting for Android Phone")
                                            .color(Color32::from_rgb(203, 213, 225)),
                                    );
                                });
                        }
                    });
                });
            });

        // Main Split Setup Content
        egui::CentralPanel::default()
            .frame(
                egui::Frame::none()
                    .fill(Color32::from_rgb(11, 17, 32))
                    .inner_margin(egui::Margin::same(20.0)),
            )
            .show(ctx, |ui| {
                ui.columns(2, |cols| {
                    // Left Column: Instant QR Code Pairing
                    egui::Frame::none()
                        .fill(Color32::from_rgb(20, 29, 49))
                        .stroke(Stroke::new(1.0, Color32::from_rgb(51, 65, 85)))
                        .rounding(Rounding::same(12.0))
                        .inner_margin(egui::Margin::same(18.0))
                        .show(&mut cols[0], |ui| {
                            ui.label(
                                RichText::new("1. Instant Pairing (QR Code)")
                                    .size(16.0)
                                    .strong()
                                    .color(Color32::WHITE),
                            );
                            ui.add_space(4.0);
                            ui.label(
                                RichText::new(
                                    "Open KeyConnect on your Android phone and scan this QR code, or select this PC from Auto-Discovery.",
                                )
                                .size(12.5)
                                .color(Color32::from_rgb(148, 163, 184)),
                            );
                            ui.add_space(14.0);

                            ui.vertical_centered(|ui| {
                                if let Some((grid_w, ref modules)) = self.cached_qr_matrix {
                                    draw_qr_code(ui, grid_w, modules, 210.0);
                                }
                                ui.add_space(10.0);
                                if ui.button("Refresh Pairing QR Token").clicked() {
                                    self.hub.rotate_qr_token();
                                }
                            });

                            ui.add_space(12.0);
                            ui.separator();
                            ui.add_space(8.0);

                            let ips_label = if snap.local_ips.is_empty() {
                                "127.0.0.1 (USB Loopback)".to_string()
                            } else {
                                snap.local_ips.join("  |  ")
                            };
                            ui.label(
                                RichText::new(format!("Host IPs: {ips_label}"))
                                    .size(12.5)
                                    .color(Color32::from_rgb(125, 211, 252)),
                            );
                            ui.label(
                                RichText::new(format!(
                                    "Ports: TCP {TCP_CONTROL_PORT} (Control/USB) • UDP {UDP_FAST_PORT} (Fast Keys)"
                                ))
                                .size(11.5)
                                .color(Color32::from_rgb(148, 163, 184)),
                            );
                        });

                    // Right Column: Multi-Transport Status + Trusted Phones + Settings
                    cols[1].vertical(|ui| {
                        // Transports Card
                        egui::Frame::none()
                            .fill(Color32::from_rgb(20, 29, 49))
                            .stroke(Stroke::new(1.0, Color32::from_rgb(51, 65, 85)))
                            .rounding(Rounding::same(12.0))
                            .inner_margin(egui::Margin::same(16.0))
                            .show(ui, |ui| {
                                ui.label(
                                    RichText::new("2. Multi-Transport Channels (Auto-Failover)")
                                        .size(15.5)
                                        .strong()
                                        .color(Color32::WHITE),
                                );
                                ui.add_space(8.0);

                                transport_row(
                                    ui,
                                    "USB Cable (ADB / RNDIS)",
                                    "Priority #1 (< 0.5 ms)",
                                    Color32::from_rgb(16, 185, 129),
                                );
                                ui.add_space(4.0);
                                transport_row(
                                    ui,
                                    "Wi-Fi LAN / Hotspot",
                                    "Priority #2 (1 - 3 ms, UDP+TCP)",
                                    Color32::from_rgb(14, 165, 233),
                                );
                                ui.add_space(4.0);
                                transport_row(
                                    ui,
                                    "Bluetooth Classic RFCOMM",
                                    "Priority #3 (Offline Wireless)",
                                    Color32::from_rgb(129, 140, 248),
                                );

                                ui.add_space(10.0);
                                let mut auto_start = snap.auto_start;
                                if ui
                                    .checkbox(
                                        &mut auto_start,
                                        "Start KeyConnect automatically with Windows",
                                    )
                                    .changed()
                                {
                                    if let Err(err) = self.hub.set_auto_start(auto_start) {
                                        self.status_toast = Some(err);
                                    }
                                }
                            });

                        ui.add_space(12.0);

                        // Trusted Phones Card
                        egui::Frame::none()
                            .fill(Color32::from_rgb(20, 29, 49))
                            .stroke(Stroke::new(1.0, Color32::from_rgb(51, 65, 85)))
                            .rounding(Rounding::same(12.0))
                            .inner_margin(egui::Margin::same(16.0))
                            .show(ui, |ui| {
                                ui.label(
                                    RichText::new(format!(
                                        "Trusted Android Phones ({})",
                                        snap.trusted_devices.len()
                                    ))
                                    .size(15.5)
                                    .strong()
                                    .color(Color32::WHITE),
                                );
                                ui.add_space(8.0);

                                if snap.trusted_devices.is_empty() {
                                    ui.label(
                                        RichText::new(
                                            "No paired phones yet. Scan the QR code or tap this PC on your phone.",
                                        )
                                        .size(12.5)
                                        .color(Color32::from_rgb(148, 163, 184)),
                                    );
                                } else {
                                    egui::ScrollArea::vertical()
                                        .max_height(150.0)
                                        .show(ui, |ui| {
                                            for dev in &snap.trusted_devices {
                                                ui.horizontal(|ui| {
                                                    ui.label(
                                                        RichText::new(format!(
                                                            "{} ({})",
                                                            dev.device_name, dev.device_id
                                                        ))
                                                        .color(Color32::from_rgb(226, 232, 240)),
                                                    );
                                                    ui.with_layout(
                                                        egui::Layout::right_to_left(
                                                            egui::Align::Center,
                                                        ),
                                                        |ui| {
                                                            if ui.small_button("Unpair").clicked() {
                                                                self.hub
                                                                    .unpair_device(&dev.device_id);
                                                            }
                                                        },
                                                    );
                                                });
                                                ui.separator();
                                            }
                                        });
                                }
                            });
                    });
                });
            });

        // KDE Connect-style 6-Digit PIN Confirmation Modal
        if let Some((_dev_id, dev_name, pin, medium)) = snap.pending_pairing_info {
            egui::Window::new("Pairing Verification Request")
                .collapsible(false)
                .resizable(false)
                .anchor(egui::Align2::CENTER_CENTER, [0.0, 0.0])
                .show(ctx, |ui| {
                    ui.set_min_width(360.0);
                    ui.vertical_centered(|ui| {
                        ui.add_space(6.0);
                        ui.label(
                            RichText::new(format!(
                                "\"{}\" wants to pair via {}",
                                dev_name,
                                medium.badge()
                            ))
                            .size(15.5)
                            .strong()
                            .color(Color32::WHITE),
                        );
                        ui.add_space(8.0);
                        ui.label(
                            RichText::new("Verify that this 6-digit security PIN matches your phone:")
                                .size(12.5)
                                .color(Color32::from_rgb(148, 163, 184)),
                        );
                        ui.add_space(12.0);
                        egui::Frame::none()
                            .fill(Color32::from_rgb(15, 23, 42))
                            .stroke(Stroke::new(1.5, Color32::from_rgb(56, 189, 248)))
                            .rounding(Rounding::same(10.0))
                            .inner_margin(egui::Margin::symmetric(28.0, 12.0))
                            .show(ui, |ui| {
                                ui.label(
                                    RichText::new(pin)
                                        .size(30.0)
                                        .strong()
                                        .monospace()
                                        .color(Color32::from_rgb(56, 189, 248)),
                                );
                            });
                        ui.add_space(16.0);
                        ui.horizontal(|ui| {
                            if ui
                                .add_sized(
                                    [165.0, 36.0],
                                    egui::Button::new(
                                        RichText::new("Accept & Trust")
                                            .strong()
                                            .color(Color32::WHITE),
                                    )
                                    .fill(Color32::from_rgb(16, 185, 129)),
                                )
                                .clicked()
                            {
                                self.hub.resolve_pending_pairing(true);
                            }
                            if ui
                                .add_sized(
                                    [165.0, 36.0],
                                    egui::Button::new(
                                        RichText::new("Reject").color(Color32::WHITE),
                                    )
                                    .fill(Color32::from_rgb(220, 38, 38)),
                                )
                                .clicked()
                            {
                                self.hub.resolve_pending_pairing(false);
                            }
                        });
                        ui.add_space(6.0);
                    });
                });
        }
    }
}

fn transport_row(ui: &mut egui::Ui, name: &str, detail: &str, dot_color: Color32) {
    ui.horizontal(|ui| {
        let (rect, _) = ui.allocate_exact_size(Vec2::splat(10.0), Sense::hover());
        ui.painter().circle_filled(rect.center(), 4.5, dot_color);
        ui.label(RichText::new(name).strong().color(Color32::WHITE));
        ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
            ui.label(
                RichText::new(detail)
                    .size(11.5)
                    .color(Color32::from_rgb(148, 163, 184)),
            );
        });
    });
}

fn draw_qr_code(ui: &mut egui::Ui, grid_w: usize, modules: &[bool], target_px: f32) {
    let quiet_zone = 3usize;
    let total_units = (grid_w + quiet_zone * 2) as f32;
    let cell_px = (target_px / total_units).floor().max(2.0);
    let actual_size = cell_px * total_units;

    let (rect, _resp) = ui.allocate_exact_size(Vec2::splat(actual_size), Sense::hover());
    let painter = ui.painter();
    painter.rect_filled(rect, Rounding::same(10.0), Color32::WHITE);

    for y in 0..grid_w {
        for x in 0..grid_w {
            if modules[y * grid_w + x] {
                let min = Pos2::new(
                    rect.min.x + ((x + quiet_zone) as f32) * cell_px,
                    rect.min.y + ((y + quiet_zone) as f32) * cell_px,
                );
                let max = Pos2::new(min.x + cell_px, min.y + cell_px);
                painter.rect_filled(
                    Rect::from_min_max(min, max),
                    Rounding::ZERO,
                    Color32::from_rgb(15, 23, 42),
                );
            }
        }
    }
}

fn configure_fonts_and_theme(ctx: &egui::Context) {
    let mut visuals = egui::Visuals::dark();
    visuals.panel_fill = Color32::from_rgb(11, 17, 32);
    ctx.set_visuals(visuals);

    let candidate_fonts = [
        "C:\\Windows\\Fonts\\segoeui.ttf",
        "C:\\Windows\\Fonts\\tahoma.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ];
    for path in candidate_fonts {
        if let Ok(bytes) = fs::read(path) {
            let mut defs = FontDefinitions::default();
            defs.font_data
                .insert("system_ui".to_string(), FontData::from_owned(bytes));
            if let Some(fam) = defs.families.get_mut(&FontFamily::Proportional) {
                fam.insert(0, "system_ui".to_string());
            }
            ctx.set_fonts(defs);
            break;
        }
    }
}
