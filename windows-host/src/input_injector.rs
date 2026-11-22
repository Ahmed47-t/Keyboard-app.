use crate::protocol::{canonical_key_to_win32_vk, is_extended_vk, KeyAction, ModifiersState};
use std::collections::HashSet;
use std::sync::Mutex;

/// Thread-safe native keyboard input injector with stuck-key safety tracking.
pub struct KeyboardInjector {
    held_vks: Mutex<HashSet<u16>>,
}

impl Default for KeyboardInjector {
    fn default() -> Self {
        Self::new()
    }
}

impl KeyboardInjector {
    pub fn new() -> Self {
        Self {
            held_vks: Mutex::new(HashSet::new()),
        }
    }

    pub fn inject_key_event(&self, action: KeyAction, key: &str, modifiers: ModifiersState) {
        if let Some(vk) = canonical_key_to_win32_vk(key) {
            match action {
                KeyAction::Down => {
                    if let Ok(mut held) = self.held_vks.lock() {
                        held.insert(vk);
                    }
                    send_vk_raw(&[(vk, false)]);
                }
                KeyAction::Up => {
                    if let Ok(mut held) = self.held_vks.lock() {
                        held.remove(&vk);
                    }
                    send_vk_raw(&[(vk, true)]);
                }
                KeyAction::Press => {
                    let mut transient_mods = Vec::new();
                    {
                        let held = self.held_vks.lock().unwrap_or_else(|e| e.into_inner());
                        if modifiers.ctrl && !held.contains(&0xA2) && !held.contains(&0xA3) {
                            transient_mods.push(0xA2u16);
                        }
                        if modifiers.alt && !held.contains(&0xA4) && !held.contains(&0xA5) {
                            transient_mods.push(0xA4u16);
                        }
                        if modifiers.shift && !held.contains(&0xA0) && !held.contains(&0xA1) {
                            transient_mods.push(0xA0u16);
                        }
                        if modifiers.meta && !held.contains(&0x5B) && !held.contains(&0x5C) {
                            transient_mods.push(0x5Bu16);
                        }
                    }

                    let mut seq = Vec::with_capacity(transient_mods.len() * 2 + 2);
                    for &m in &transient_mods {
                        seq.push((m, false));
                    }
                    seq.push((vk, false));
                    seq.push((vk, true));
                    for &m in transient_mods.iter().rev() {
                        seq.push((m, true));
                    }
                    send_vk_raw(&seq);
                }
            }
        } else if matches!(action, KeyAction::Down | KeyAction::Press) && !key.is_empty() {
            // If `key` is a direct Arabic/Unicode character label (e.g. "ض", "لا", "؟")
            if modifiers.ctrl || modifiers.alt || modifiers.meta {
                // Nothing to map without physical VK
            } else {
                self.inject_unicode_text(key);
            }
        }
    }

    /// Injects arbitrary UTF-8 strings (Arabic script, emojis, voice dictation, swipe words)
    /// directly via Win32 `KEYEVENTF_UNICODE` without needing to switch Windows keyboard language.
    pub fn inject_unicode_text(&self, text: &str) {
        if text.is_empty() {
            return;
        }
        send_unicode_string(text);
    }

    pub fn inject_shortcut_combo(&self, modifiers: &[String], key: &str) {
        let mut mod_vks = Vec::new();
        for m in modifiers {
            let m_vk = match m.to_ascii_lowercase().as_str() {
                "ctrl" | "control" => Some(0xA2u16),
                "alt" => Some(0xA4u16),
                "shift" => Some(0xA0u16),
                "meta" | "win" | "super" => Some(0x5Bu16),
                other => canonical_key_to_win32_vk(other),
            };
            if let Some(vk) = m_vk {
                if !mod_vks.contains(&vk) {
                    mod_vks.push(vk);
                }
            }
        }

        if let Some(main_vk) = canonical_key_to_win32_vk(key) {
            let mut seq = Vec::with_capacity(mod_vks.len() * 2 + 2);
            for &m in &mod_vks {
                seq.push((m, false));
            }
            seq.push((main_vk, false));
            seq.push((main_vk, true));
            for &m in mod_vks.iter().rev() {
                seq.push((m, true));
            }
            send_vk_raw(&seq);
        }
    }

    /// Releases all currently held keys (called automatically on session disconnect or manually).
    pub fn release_all_held_keys(&self) {
        let to_release: Vec<u16> = if let Ok(mut held) = self.held_vks.lock() {
            let keys: Vec<u16> = held.iter().copied().collect();
            held.clear();
            keys
        } else {
            Vec::new()
        };

        if !to_release.is_empty() {
            let seq: Vec<(u16, bool)> = to_release.into_iter().map(|vk| (vk, true)).collect();
            send_vk_raw(&seq);
        }
    }
}

#[cfg(windows)]
fn send_vk_raw(events: &[(u16, bool)]) {
    use std::mem::size_of;
    use windows_sys::Win32::UI::Input::KeyboardAndMouse::{
        MapVirtualKeyW, SendInput, INPUT, INPUT_0, INPUT_KEYBOARD, KEYBDINPUT,
        KEYEVENTF_EXTENDEDKEY, KEYEVENTF_KEYUP, MAPVK_VK_TO_VSC,
    };

    if events.is_empty() {
        return;
    }

    let mut inputs: Vec<INPUT> = Vec::with_capacity(events.len());
    for &(vk, is_up) in events {
        let scan = unsafe { MapVirtualKeyW(vk as u32, MAPVK_VK_TO_VSC) as u16 };
        let mut flags = if is_extended_vk(vk) {
            KEYEVENTF_EXTENDEDKEY
        } else {
            0
        };
        if is_up {
            flags |= KEYEVENTF_KEYUP;
        }

        inputs.push(INPUT {
            r#type: INPUT_KEYBOARD,
            Anonymous: INPUT_0 {
                ki: KEYBDINPUT {
                    wVk: vk,
                    wScan: scan,
                    dwFlags: flags,
                    time: 0,
                    dwExtraInfo: 0,
                },
            },
        });
    }

    unsafe {
        let _ = SendInput(
            inputs.len() as u32,
            inputs.as_ptr(),
            size_of::<INPUT>() as i32,
        );
    }
}

#[cfg(not(windows))]
fn send_vk_raw(_events: &[(u16, bool)]) {
    // Non-Windows stub so `cargo test` compiles and passes on Linux/macOS CI.
}

#[cfg(windows)]
fn send_unicode_string(text: &str) {
    use std::mem::size_of;
    use windows_sys::Win32::UI::Input::KeyboardAndMouse::{
        MapVirtualKeyW, SendInput, INPUT, INPUT_0, INPUT_KEYBOARD, KEYBDINPUT, KEYEVENTF_KEYUP,
        KEYEVENTF_UNICODE, MAPVK_VK_TO_VSC,
    };

    let mut inputs: Vec<INPUT> = Vec::with_capacity(text.len() * 2);
    let mut chars = text.chars().peekable();

    while let Some(ch) = chars.next() {
        if ch == '\r' {
            if chars.peek() == Some(&'\n') {
                continue;
            }
            append_vk_tap(&mut inputs, 0x0D);
        } else if ch == '\n' {
            append_vk_tap(&mut inputs, 0x0D);
        } else if ch == '\t' {
            append_vk_tap(&mut inputs, 0x09);
        } else if ch == '\u{0008}' {
            append_vk_tap(&mut inputs, 0x08);
        } else {
            let mut utf16_buf = [0u16; 2];
            for &unit in ch.encode_utf16(&mut utf16_buf).iter() {
                inputs.push(INPUT {
                    r#type: INPUT_KEYBOARD,
                    Anonymous: INPUT_0 {
                        ki: KEYBDINPUT {
                            wVk: 0,
                            wScan: unit,
                            dwFlags: KEYEVENTF_UNICODE,
                            time: 0,
                            dwExtraInfo: 0,
                        },
                    },
                });
                inputs.push(INPUT {
                    r#type: INPUT_KEYBOARD,
                    Anonymous: INPUT_0 {
                        ki: KEYBDINPUT {
                            wVk: 0,
                            wScan: unit,
                            dwFlags: KEYEVENTF_UNICODE | KEYEVENTF_KEYUP,
                            time: 0,
                            dwExtraInfo: 0,
                        },
                    },
                });
            }
        }
    }

    fn append_vk_tap(inputs: &mut Vec<INPUT>, vk: u16) {
        let scan = unsafe { MapVirtualKeyW(vk as u32, MAPVK_VK_TO_VSC) as u16 };
        inputs.push(INPUT {
            r#type: INPUT_KEYBOARD,
            Anonymous: INPUT_0 {
                ki: KEYBDINPUT {
                    wVk: vk,
                    wScan: scan,
                    dwFlags: 0,
                    time: 0,
                    dwExtraInfo: 0,
                },
            },
        });
        inputs.push(INPUT {
            r#type: INPUT_KEYBOARD,
            Anonymous: INPUT_0 {
                ki: KEYBDINPUT {
                    wVk: vk,
                    wScan: scan,
                    dwFlags: KEYEVENTF_KEYUP,
                    time: 0,
                    dwExtraInfo: 0,
                },
            },
        });
    }

    if !inputs.is_empty() {
        unsafe {
            let _ = SendInput(
                inputs.len() as u32,
                inputs.as_ptr(),
                size_of::<INPUT>() as i32,
            );
        }
    }
}

#[cfg(not(windows))]
fn send_unicode_string(_text: &str) {}
