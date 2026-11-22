package com.keyconnect.app.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator

object ProtocolConstants {
    const val PROTOCOL_VERSION = 1
    const val UDP_DISCOVERY_PORT = 45820
    const val TCP_CONTROL_PORT = 45821
    const val UDP_FAST_PORT = 45822
    const val WS_COMPANION_PORT = 45823
    const val BT_SERVICE_UUID = "8e7f1a20-4b9c-4d1e-9a6b-5c3d2e1f0a9b"

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }
}

@Serializable
enum class TransportMedium {
    @SerialName("usb")
    USB,

    @SerialName("wifi")
    WIFI,

    @SerialName("bluetooth")
    BLUETOOTH,

    @SerialName("web")
    WEB;

    val arabicLabel: String
        get() = when (this) {
            USB -> "سلك USB"
            WIFI -> "واي فاي Wi-Fi"
            BLUETOOTH -> "بلوتوث"
            WEB -> "ويب"
        }

    val badge: String
        get() = when (this) {
            USB -> "⚡ USB"
            WIFI -> "📶 Wi-Fi"
            BLUETOOTH -> "🔵 BT"
            WEB -> "🌐 Web"
        }

    val priorityScore: Int
        get() = when (this) {
            USB -> 100
            WIFI -> 80
            BLUETOOTH -> 60
            WEB -> 40
        }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("packet_type")
sealed interface DiscoveryPacket {
    @Serializable
    @SerialName("KC_DISCOVER_V1")
    data class KcDiscoverV1(
        @SerialName("device_id") val deviceId: String,
        @SerialName("device_name") val deviceName: String,
        @SerialName("protocol_version") val protocolVersion: Int = ProtocolConstants.PROTOCOL_VERSION,
        @SerialName("public_key_hex") val publicKeyHex: String,
    ) : DiscoveryPacket

    @Serializable
    @SerialName("KC_ANNOUNCE_V1")
    data class KcAnnounceV1(
        @SerialName("device_id") val deviceId: String,
        @SerialName("device_name") val deviceName: String,
        @SerialName("protocol_version") val protocolVersion: Int = ProtocolConstants.PROTOCOL_VERSION,
        @SerialName("tcp_port") val tcpPort: Int = ProtocolConstants.TCP_CONTROL_PORT,
        @SerialName("udp_fast_port") val udpFastPort: Int = ProtocolConstants.UDP_FAST_PORT,
        @SerialName("ws_port") val wsPort: Int = ProtocolConstants.WS_COMPANION_PORT,
        @SerialName("public_key_hex") val publicKeyHex: String,
        @SerialName("is_paired") val isPaired: Boolean = false,
    ) : DiscoveryPacket
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("frame")
sealed interface WireFrame {
    @Serializable
    @SerialName("client_hello")
    data class ClientHello(
        @SerialName("device_id") val deviceId: String,
        @SerialName("device_name") val deviceName: String,
        @SerialName("public_key_hex") val publicKeyHex: String,
        @SerialName("client_nonce_hex") val clientNonceHex: String,
        val medium: TransportMedium,
        @SerialName("qr_pairing_token") val qrPairingToken: String? = null,
    ) : WireFrame

    @Serializable
    @SerialName("host_hello")
    data class HostHello(
        @SerialName("device_id") val deviceId: String,
        @SerialName("device_name") val deviceName: String,
        @SerialName("public_key_hex") val publicKeyHex: String,
        @SerialName("host_nonce_hex") val hostNonceHex: String,
        @SerialName("session_id_hex") val sessionIdHex: String,
        val paired: Boolean,
        @SerialName("pairing_pin") val pairingPin: String? = null,
    ) : WireFrame

    @Serializable
    @SerialName("pairing_decision")
    data class PairingDecision(
        val accepted: Boolean,
        val reason: String? = null,
    ) : WireFrame

    @Serializable
    @SerialName("encrypted")
    data class Encrypted(
        val seq: Long,
        @SerialName("ciphertext_b64") val ciphertextB64: String,
    ) : WireFrame
}

@Serializable
enum class KeyAction {
    @SerialName("down")
    DOWN,

    @SerialName("up")
    UP,

    @SerialName("press")
    PRESS
}

@Serializable
data class ModifiersState(
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false,
    val meta: Boolean = false,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface ClientMessage {
    @Serializable
    @SerialName("key_event")
    data class KeyEvent(
        @SerialName("event_id") val eventId: Long,
        val action: KeyAction,
        val key: String,
        val modifiers: ModifiersState = ModifiersState(),
    ) : ClientMessage

    @Serializable
    @SerialName("text_commit")
    data class TextCommit(
        @SerialName("event_id") val eventId: Long,
        val text: String,
    ) : ClientMessage

    @Serializable
    @SerialName("shortcut_combo")
    data class ShortcutCombo(
        @SerialName("event_id") val eventId: Long,
        val modifiers: List<String>,
        val key: String,
    ) : ClientMessage

    @Serializable
    @SerialName("release_all")
    data class ReleaseAll(
        @SerialName("event_id") val eventId: Long,
    ) : ClientMessage

    @Serializable
    @SerialName("ping")
    data class Ping(
        @SerialName("timestamp_ms") val timestampMs: Long,
        val medium: TransportMedium,
    ) : ClientMessage
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface HostMessage {
    @Serializable
    @SerialName("pong")
    data class Pong(
        @SerialName("client_timestamp_ms") val clientTimestampMs: Long,
        @SerialName("active_medium") val activeMedium: TransportMedium,
    ) : HostMessage

    @Serializable
    @SerialName("ack")
    data class Ack(
        @SerialName("up_to_event_id") val upToEventId: Long,
    ) : HostMessage

    @Serializable
    @SerialName("disconnect_notice")
    data class DisconnectNotice(
        val reason: String,
    ) : HostMessage
}
