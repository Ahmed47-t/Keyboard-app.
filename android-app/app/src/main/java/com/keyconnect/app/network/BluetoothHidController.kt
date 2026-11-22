package com.keyconnect.app.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.keyconnect.app.model.KeyAction
import com.keyconnect.app.model.ModifiersState
import java.util.concurrent.Executors

/**
 * Hardware-level Bluetooth HID Keyboard Profile (Android 9.0 / API 28+).
 * Allows the phone to act as a standard plug-and-play Bluetooth Keyboard even on
 * Windows Lock Screen / BitLocker / BIOS without requiring the host daemon.
 */
@SuppressLint("MissingPermission")
class BluetoothHidController(private val context: Context) {

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedHost: BluetoothDevice? = null
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    val isConnected: Boolean
        get() = connectedHost != null && hidDevice != null

    fun startHidProfile(targetAddress: String?, onStateChanged: (Boolean, String) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            onStateChanged(false, "يتطلب وضع Bluetooth HID نظام Android 9 أو أحدث")
            return
        }

        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter? = btManager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            onStateChanged(false, "يرجى تفعيل البلوتوث أولاً")
            return
        }

        adapter.getProfileProxy(
            context,
            object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                    if (profile != BluetoothProfile.HID_DEVICE) return
                    val hid = proxy as? BluetoothHidDevice ?: return
                    hidDevice = hid

                    val sdp = BluetoothHidDeviceAppSdpSettings(
                        "KeyConnect Keyboard",
                        "Full-Screen Mobile PC Keyboard",
                        "KeyConnect",
                        BluetoothHidDevice.SUBCLASS1_KEYBOARD,
                        BOOT_KEYBOARD_REPORT_DESCRIPTOR
                    )

                    hid.registerApp(
                        sdp,
                        null,
                        null,
                        callbackExecutor,
                        object : BluetoothHidDevice.Callback() {
                            override fun onAppStatusChanged(
                                pluggedDevice: BluetoothDevice?,
                                registered: Boolean,
                            ) {
                                if (!registered) {
                                    onStateChanged(false, "تعذر تسجيل ملف تعريف لوحة مفاتيح البلوتوث")
                                    return
                                }
                                val target = targetAddress?.let { addr ->
                                    adapter.bondedDevices?.firstOrNull { it.address == addr }
                                } ?: pluggedDevice ?: adapter.bondedDevices?.firstOrNull()

                                if (target != null) {
                                    hid.connect(target)
                                } else {
                                    onStateChanged(false, "اقرن الهاتف بالكمبيوتر من إعدادات البلوتوث أولاً")
                                }
                            }

                            override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
                                when (state) {
                                    BluetoothProfile.STATE_CONNECTED -> {
                                        connectedHost = device
                                        onStateChanged(true, device?.name ?: "Bluetooth HID Host")
                                    }
                                    BluetoothProfile.STATE_DISCONNECTED -> {
                                        if (connectedHost?.address == device?.address) {
                                            connectedHost = null
                                            onStateChanged(false, "انقطع اتصال Bluetooth HID")
                                        }
                                    }
                                }
                            }
                        }
                    )
                }

                override fun onServiceDisconnected(profile: Int) {
                    if (profile == BluetoothProfile.HID_DEVICE) {
                        hidDevice = null
                        connectedHost = null
                    }
                }
            },
            BluetoothProfile.HID_DEVICE
        )
    }

    fun sendHidKeyEvent(action: KeyAction, canonicalKey: String, modifiers: ModifiersState): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val hid = hidDevice ?: return false
        val host = connectedHost ?: return false
        val usage = canonicalKeyToHidUsage(canonicalKey) ?: return false

        var modByte = 0
        if (modifiers.ctrl || canonicalKey == "CtrlLeft") modByte = modByte or 0x01
        if (modifiers.shift || canonicalKey == "ShiftLeft") modByte = modByte or 0x02
        if (modifiers.alt || canonicalKey == "AltLeft") modByte = modByte or 0x04
        if (modifiers.meta || canonicalKey == "MetaLeft") modByte = modByte or 0x08

        return when (action) {
            KeyAction.DOWN -> {
                val report = byteArrayOf(modByte.toByte(), 0, usage, 0, 0, 0, 0, 0)
                hid.sendReport(host, 1, report)
            }
            KeyAction.UP -> {
                val release = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0)
                hid.sendReport(host, 1, release)
            }
            KeyAction.PRESS -> {
                val down = byteArrayOf(modByte.toByte(), 0, usage, 0, 0, 0, 0, 0)
                val up = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0)
                hid.sendReport(host, 1, down) && hid.sendReport(host, 1, up)
            }
        }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { hidDevice?.unregisterApp() }
        }
        connectedHost = null
        hidDevice = null
    }

    private fun canonicalKeyToHidUsage(key: String): Byte? {
        val code: Int = when (key) {
            "KeyA", "a", "A" -> 0x04
            "KeyB", "b", "B" -> 0x05
            "KeyC", "c", "C" -> 0x06
            "KeyD", "d", "D" -> 0x07
            "KeyE", "e", "E" -> 0x08
            "KeyF", "f", "F" -> 0x09
            "KeyG", "g", "G" -> 0x0A
            "KeyH", "h", "H" -> 0x0B
            "KeyI", "i", "I" -> 0x0C
            "KeyJ", "j", "J" -> 0x0D
            "KeyK", "k", "K" -> 0x0E
            "KeyL", "l", "L" -> 0x0F
            "KeyM", "m", "M" -> 0x10
            "KeyN", "n", "N" -> 0x11
            "KeyO", "o", "O" -> 0x12
            "KeyP", "p", "P" -> 0x13
            "KeyQ", "q", "Q" -> 0x14
            "KeyR", "r", "R" -> 0x15
            "KeyS", "s", "S" -> 0x16
            "KeyT", "t", "T" -> 0x17
            "KeyU", "u", "U" -> 0x18
            "KeyV", "v", "V" -> 0x19
            "KeyW", "w", "W" -> 0x1A
            "KeyX", "x", "X" -> 0x1B
            "KeyY", "y", "Y" -> 0x1C
            "KeyZ", "z", "Z" -> 0x1D
            "Digit1", "1" -> 0x1E
            "Digit2", "2" -> 0x1F
            "Digit3", "3" -> 0x20
            "Digit4", "4" -> 0x21
            "Digit5", "5" -> 0x22
            "Digit6", "6" -> 0x23
            "Digit7", "7" -> 0x24
            "Digit8", "8" -> 0x25
            "Digit9", "9" -> 0x26
            "Digit0", "0" -> 0x27
            "Enter" -> 0x28
            "Escape", "Esc" -> 0x29
            "Backspace" -> 0x2A
            "Tab" -> 0x2B
            "Space", " " -> 0x2C
            "Minus", "-" -> 0x2D
            "Equal", "=" -> 0x2E
            "BracketLeft", "[" -> 0x2F
            "BracketRight", "]" -> 0x30
            "Backslash", "\\" -> 0x31
            "Semicolon", ";" -> 0x33
            "Quote", "'" -> 0x34
            "Backquote", "`" -> 0x35
            "Comma", "," -> 0x36
            "Period", "." -> 0x37
            "Slash", "/" -> 0x38
            "CapsLock" -> 0x39
            "F1" -> 0x3A
            "F2" -> 0x3B
            "F3" -> 0x3C
            "F4" -> 0x3D
            "F5" -> 0x3E
            "F6" -> 0x3F
            "F7" -> 0x40
            "F8" -> 0x41
            "F9" -> 0x42
            "F10" -> 0x43
            "F11" -> 0x44
            "F12" -> 0x45
            "Delete", "Del" -> 0x4C
            "ArrowRight", "Right" -> 0x4F
            "ArrowLeft", "Left" -> 0x50
            "ArrowDown", "Down" -> 0x51
            "ArrowUp", "Up" -> 0x52
            "CtrlLeft", "ShiftLeft", "AltLeft", "MetaLeft" -> 0x00
            else -> return null
        }
        return code.toByte()
    }

    companion object {
        // Standard 63-byte USB/BT Boot Keyboard HID Report Descriptor (Report ID = 1)
        private val BOOT_KEYBOARD_REPORT_DESCRIPTOR = byteArrayOf(
            0x05.toByte(), 0x01.toByte(), // Usage Page (Generic Desktop)
            0x09.toByte(), 0x06.toByte(), // Usage (Keyboard)
            0xA1.toByte(), 0x01.toByte(), // Collection (Application)
            0x85.toByte(), 0x01.toByte(), //   Report ID (1)
            0x05.toByte(), 0x07.toByte(), //   Usage Page (Key Codes)
            0x19.toByte(), 0xE0.toByte(), //   Usage Minimum (224 - LeftControl)
            0x29.toByte(), 0xE7.toByte(), //   Usage Maximum (231 - RightGUI)
            0x15.toByte(), 0x00.toByte(), //   Logical Minimum (0)
            0x25.toByte(), 0x01.toByte(), //   Logical Maximum (1)
            0x75.toByte(), 0x01.toByte(), //   Report Size (1)
            0x95.toByte(), 0x08.toByte(), //   Report Count (8)
            0x81.toByte(), 0x02.toByte(), //   Input (Data, Variable, Absolute)
            0x95.toByte(), 0x01.toByte(), //   Report Count (1)
            0x75.toByte(), 0x08.toByte(), //   Report Size (8)
            0x81.toByte(), 0x01.toByte(), //   Input (Constant - Reserved byte)
            0x95.toByte(), 0x06.toByte(), //   Report Count (6)
            0x75.toByte(), 0x08.toByte(), //   Report Size (8)
            0x15.toByte(), 0x00.toByte(), //   Logical Minimum (0)
            0x25.toByte(), 0x65.toByte(), //   Logical Maximum (101)
            0x05.toByte(), 0x07.toByte(), //   Usage Page (Key Codes)
            0x19.toByte(), 0x00.toByte(), //   Usage Minimum (0)
            0x29.toByte(), 0x65.toByte(), //   Usage Maximum (101)
            0x81.toByte(), 0x00.toByte(), //   Input (Data, Array)
            0xC0.toByte()                 // End Collection
        )
    }
}
