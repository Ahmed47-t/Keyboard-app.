package com.keyconnect.app.model

import kotlinx.serialization.Serializable

@Serializable
data class TrustedHost(
    val deviceId: String,
    val deviceName: String,
    val publicKeyHex: String,
    val lastKnownIps: List<String> = emptyList(),
    val bluetoothAddress: String? = null,
    val tcpPort: Int = ProtocolConstants.TCP_CONTROL_PORT,
    val udpFastPort: Int = ProtocolConstants.UDP_FAST_PORT,
)

data class DiscoveredHost(
    val deviceId: String,
    val deviceName: String,
    val publicKeyHex: String,
    val wifiIp: String? = null,
    val usbAvailable: Boolean = false,
    val usbIp: String? = null,
    val bluetoothAddress: String? = null,
    val tcpPort: Int = ProtocolConstants.TCP_CONTROL_PORT,
    val udpFastPort: Int = ProtocolConstants.UDP_FAST_PORT,
    val isTrusted: Boolean = false,
    val lastSeenMs: Long = System.currentTimeMillis(),
) {
    val availableMediums: Set<TransportMedium>
        get() = buildSet {
            if (usbAvailable) add(TransportMedium.USB)
            if (wifiIp != null) add(TransportMedium.WIFI)
            if (bluetoothAddress != null) add(TransportMedium.BLUETOOTH)
        }
}

enum class TransportPreference {
    AUTO_SMART,      // USB > Wi-Fi > Bluetooth automatic failover
    USB_ONLY,
    WIFI_ONLY,
    BLUETOOTH_ONLY,
    BLUETOOTH_HID    // Driverless Hardware Bluetooth Keyboard profile
}

enum class KeyboardSurfaceMode {
    PC_FULL_CUSTOM,  // 6-row Full-Screen Custom PC Keyboard (AR 101 + EN QWERTY + Fn)
    SYSTEM_IME       // Native Phone Keyboard (Gboard / Voice Dictation / Swipe) + Windows Control Deck
}

enum class KeyboardLanguageLayer {
    ARABIC_101,
    ENGLISH_QWERTY,
    FN_NUMPAD
}

sealed interface ConnectionUiState {
    data object Disconnected : ConnectionUiState
    data class Connecting(val hostName: String, val medium: TransportMedium) : ConnectionUiState
    data class AwaitingPairingPin(val hostName: String, val pin: String, val medium: TransportMedium) : ConnectionUiState
    data class Connected(
        val hostId: String,
        val hostName: String,
        val activeMedium: TransportMedium,
        val standbyMediums: Set<TransportMedium>,
        val rttMs: Long,
        val sentEventsCount: Long,
    ) : ConnectionUiState
    data class Error(val message: String) : ConnectionUiState
}
