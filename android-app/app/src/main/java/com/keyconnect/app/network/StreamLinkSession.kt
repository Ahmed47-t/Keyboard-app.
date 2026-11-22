package com.keyconnect.app.network

import com.keyconnect.app.crypto.CipherDirection
import com.keyconnect.app.crypto.HexCodec
import com.keyconnect.app.crypto.KeyConnectCrypto
import com.keyconnect.app.crypto.SessionAeadCipher
import com.keyconnect.app.crypto.StaticKeyIdentity
import com.keyconnect.app.model.ClientMessage
import com.keyconnect.app.model.HostMessage
import com.keyconnect.app.model.ProtocolConstants
import com.keyconnect.app.model.TransportMedium
import com.keyconnect.app.model.WireFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicLong

/**
 * Authenticated, encrypted transport link running over TCP (Wi-Fi / USB ADB / USB RNDIS)
 * or Bluetooth Classic RFCOMM, with optional UDP fast-path datagram acceleration.
 */
class StreamLinkSession(
    val medium: TransportMedium,
    private val rawInput: InputStream,
    private val rawOutput: OutputStream,
    private val underlyingCloseable: Closeable,
    private val udpFastTarget: Pair<InetAddress, Int>? = null,
) {
    private val dataIn = DataInputStream(rawInput.buffered())
    private val dataOut = DataOutputStream(rawOutput.buffered())
    private val writeMutex = Mutex()
    private val outboundSeq = AtomicLong(0L)

    private var cipher: SessionAeadCipher? = null
    private var udpSocket: DatagramSocket? = null
    private var readerJob: Job? = null
    private var pingJob: Job? = null

    @Volatile
    var isAlive: Boolean = false
        private set

    @Volatile
    var latestRttMs: Long = when (medium) {
        TransportMedium.USB -> 1L
        TransportMedium.WIFI -> 2L
        TransportMedium.BLUETOOTH -> 6L
        TransportMedium.WEB -> 4L
    }
        private set

    var hostDeviceId: String = ""
        private set
    var hostDeviceName: String = ""
        private set
    var hostPublicKeyHex: String = ""
        private set

    suspend fun performHandshake(
        clientIdentity: StaticKeyIdentity,
        clientDeviceName: String,
        qrPairingToken: String? = null,
        onPinVerificationRequired: (hostName: String, pin: String) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val clientNonce = KeyConnectCrypto.randomBytes(16)
            val hello = WireFrame.ClientHello(
                deviceId = clientIdentity.deviceId,
                deviceName = clientDeviceName,
                publicKeyHex = clientIdentity.publicHex,
                clientNonceHex = HexCodec.encode(clientNonce),
                medium = medium,
                qrPairingToken = qrPairingToken,
            )
            writeFrame(hello)

            val response = readFrame()
            val hostHello = response as? WireFrame.HostHello
                ?: error("Unexpected handshake response from host")

            hostDeviceId = hostHello.deviceId
            hostDeviceName = hostHello.deviceName
            hostPublicKeyHex = hostHello.publicKeyHex

            val hostPubBytes = HexCodec.decode(hostHello.publicKeyHex)
            val hostNonceBytes = HexCodec.decode(hostHello.hostNonceHex)
            val sessionIdBytes = HexCodec.decode(hostHello.sessionIdHex)

            val sharedSecret = clientIdentity.diffieHellman(hostPubBytes)
            val sessionKey = KeyConnectCrypto.deriveSessionKey(sharedSecret, clientNonce, hostNonceBytes)
            val establishedCipher = SessionAeadCipher(sessionKey, sessionIdBytes)

            if (!hostHello.paired) {
                val computedPin = KeyConnectCrypto.computePairingPin(
                    clientIdentity.publicBytes,
                    hostPubBytes
                )
                onPinVerificationRequired(hostHello.deviceName, computedPin)

                val decisionFrame = readFrame()
                val decision = decisionFrame as? WireFrame.PairingDecision
                    ?: error("Expected PairingDecision frame")
                if (!decision.accepted) {
                    error(decision.reason ?: "تم رفض الإقران من الحاسوب")
                }
            }

            cipher = establishedCipher
            if (udpFastTarget != null) {
                runCatching { udpSocket = DatagramSocket() }
            }
            isAlive = true
        }
    }

    fun startBackgroundLoops(
        scope: CoroutineScope,
        onAckReceived: (Long) -> Unit,
        onDisconnected: (TransportMedium) -> Unit,
    ) {
        readerJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive && isAlive) {
                    val frame = readFrame()
                    if (frame is WireFrame.Encrypted) {
                        val activeCipher = cipher ?: break
                        val plain = activeCipher.openBase64(
                            CipherDirection.HOST_TO_CLIENT,
                            frame.seq,
                            frame.ciphertextB64
                        )
                        val msg = ProtocolConstants.json.decodeFromString(
                            HostMessage.serializer(),
                            plain.decodeToString()
                        )
                        when (msg) {
                            is HostMessage.Pong -> {
                                val rtt = (System.currentTimeMillis() - msg.clientTimestampMs)
                                    .coerceAtLeast(1L)
                                latestRttMs = rtt
                            }
                            is HostMessage.Ack -> onAckReceived(msg.upToEventId)
                            is HostMessage.DisconnectNotice -> break
                        }
                    }
                }
            } catch (_: Exception) {
                // Socket closed or broken pipe
            } finally {
                isAlive = false
                closeQuietly()
                onDisconnected(medium)
            }
        }

        pingJob = scope.launch(Dispatchers.IO) {
            while (isActive && isAlive) {
                delay(2000L)
                val ok = sendEncryptedReliable(
                    ClientMessage.Ping(
                        timestampMs = System.currentTimeMillis(),
                        medium = medium
                    )
                )
                if (!ok) break
            }
        }
    }

    /**
     * Sends a keystroke over UDP fast-path when available (zero TCP head-of-line blocking)
     * or directly over TCP_NODELAY / RFCOMM stream.
     */
    suspend fun sendClientMessage(msg: ClientMessage, preferFastUdp: Boolean = false): Boolean {
        if (!isAlive) return false
        val target = udpFastTarget
        val sock = udpSocket
        val activeCipher = cipher ?: return false

        if (preferFastUdp && target != null && sock != null) {
            val sentUdp = withContext(Dispatchers.IO) {
                runCatching {
                    val seq = outboundSeq.incrementAndGet()
                    val plain = ProtocolConstants.json
                        .encodeToString(ClientMessage.serializer(), msg)
                        .toByteArray(Charsets.UTF_8)
                    val sealed = activeCipher.seal(CipherDirection.CLIENT_TO_HOST, seq, plain)
                    val buf = ByteBuffer.allocate(16 + sealed.size)
                    buf.put(activeCipher.sessionId8)
                    buf.putLong(seq)
                    buf.put(sealed)
                    val bytes = buf.array()
                    sock.send(DatagramPacket(bytes, bytes.size, target.first, target.second))
                    true
                }.getOrDefault(false)
            }
            if (sentUdp) return true
        }

        return sendEncryptedReliable(msg)
    }

    suspend fun sendEncryptedReliable(msg: ClientMessage): Boolean = withContext(Dispatchers.IO) {
        val activeCipher = cipher ?: return@withContext false
        runCatching {
            val seq = outboundSeq.incrementAndGet()
            val plain = ProtocolConstants.json
                .encodeToString(ClientMessage.serializer(), msg)
                .toByteArray(Charsets.UTF_8)
            val b64 = activeCipher.sealBase64(CipherDirection.CLIENT_TO_HOST, seq, plain)
            writeFrame(WireFrame.Encrypted(seq = seq, ciphertextB64 = b64))
            true
        }.getOrElse {
            isAlive = false
            false
        }
    }

    private suspend fun writeFrame(frame: WireFrame) = writeMutex.withLock {
        val bytes = ProtocolConstants.json
            .encodeToString(WireFrame.serializer(), frame)
            .toByteArray(Charsets.UTF_8)
        dataOut.writeInt(bytes.size)
        dataOut.write(bytes)
        dataOut.flush()
    }

    private fun readFrame(): WireFrame {
        val len = dataIn.readInt()
        require(len in 1..65_536) { "Invalid WireFrame length: $len" }
        val buf = ByteArray(len)
        dataIn.readFully(buf)
        return ProtocolConstants.json.decodeFromString(
            WireFrame.serializer(),
            buf.decodeToString()
        )
    }

    suspend fun shutdown() {
        isAlive = false
        closeQuietly()
        pingJob?.cancelAndJoin()
        readerJob?.cancelAndJoin()
    }

    private fun closeQuietly() {
        runCatching { udpSocket?.close() }
        runCatching { underlyingCloseable.close() }
    }
}
