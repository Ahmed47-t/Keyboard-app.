package com.keyconnect.app.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.KeyParameter
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

enum class CipherDirection(val prefix: ByteArray) {
    CLIENT_TO_HOST(byteArrayOf(0x43, 0x32, 0x48, 0x01)), // "C2H\x01"
    HOST_TO_CLIENT(byteArrayOf(0x48, 0x32, 0x43, 0x01))  // "H2C\x01"
}

class StaticKeyIdentity private constructor(
    private val privateParams: X25519PrivateKeyParameters,
    private val publicParams: X25519PublicKeyParameters,
) {
    companion object {
        private val rng = SecureRandom()

        fun generate(): StaticKeyIdentity {
            val priv = X25519PrivateKeyParameters(rng)
            return StaticKeyIdentity(priv, priv.generatePublicKey())
        }

        fun fromSecretSeed(seed32: ByteArray): StaticKeyIdentity {
            require(seed32.size == 32) { "X25519 seed must be 32 bytes" }
            val priv = X25519PrivateKeyParameters(seed32, 0)
            return StaticKeyIdentity(priv, priv.generatePublicKey())
        }

        fun fromSecretHex(hex: String): StaticKeyIdentity =
            fromSecretSeed(HexCodec.decode(hex))
    }

    val secretBytes: ByteArray
        get() = privateParams.encoded

    val publicBytes: ByteArray
        get() = publicParams.encoded

    val secretHex: String
        get() = HexCodec.encode(secretBytes)

    val publicHex: String
        get() = HexCodec.encode(publicBytes)

    val deviceId: String
        get() = KeyConnectCrypto.deriveDeviceId(publicBytes)

    fun diffieHellman(peerPublicBytes: ByteArray): ByteArray {
        require(peerPublicBytes.size == 32) { "Peer X25519 public key must be 32 bytes" }
        val peerPub = X25519PublicKeyParameters(peerPublicBytes, 0)
        val shared = ByteArray(32)
        privateParams.generateSecret(peerPub, shared, 0)
        return shared
    }
}

class SessionAeadCipher(
    private val sessionKey32: ByteArray,
    val sessionId8: ByteArray,
) {
    init {
        require(sessionKey32.size == 32) { "ChaCha20-Poly1305 key must be 32 bytes" }
        require(sessionId8.size == 8) { "Session ID AAD must be 8 bytes" }
    }

    private fun buildNonce(direction: CipherDirection, seq: Long): ByteArray {
        val buf = ByteBuffer.allocate(12)
        buf.put(direction.prefix)
        buf.putLong(seq)
        return buf.array()
    }

    @Synchronized
    fun seal(direction: CipherDirection, seq: Long, plaintext: ByteArray): ByteArray {
        val nonce = buildNonce(direction, seq)
        val aead = ChaCha20Poly1305()
        aead.init(
            true,
            AEADParameters(KeyParameter(sessionKey32), 128, nonce, sessionId8)
        )
        val out = ByteArray(aead.getOutputSize(plaintext.size))
        val len = aead.processBytes(plaintext, 0, plaintext.size, out, 0)
        aead.doFinal(out, len)
        return out
    }

    @Synchronized
    fun open(direction: CipherDirection, seq: Long, ciphertextWithTag: ByteArray): ByteArray {
        val nonce = buildNonce(direction, seq)
        val aead = ChaCha20Poly1305()
        aead.init(
            false,
            AEADParameters(KeyParameter(sessionKey32), 128, nonce, sessionId8)
        )
        val out = ByteArray(aead.getOutputSize(ciphertextWithTag.size))
        val len = aead.processBytes(ciphertextWithTag, 0, ciphertextWithTag.size, out, 0)
        aead.doFinal(out, len)
        return out
    }

    fun sealBase64(direction: CipherDirection, seq: Long, plaintext: ByteArray): String =
        Base64.getEncoder().encodeToString(seal(direction, seq, plaintext))

    fun openBase64(direction: CipherDirection, seq: Long, ciphertextB64: String): ByteArray =
        open(direction, seq, Base64.getDecoder().decode(ciphertextB64))
}

object KeyConnectCrypto {
    private val rng = SecureRandom()

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { rng.nextBytes(it) }

    fun deriveDeviceId(publicBytes32: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(publicBytes32)
        return HexCodec.encode(digest.copyOfRange(0, 8))
    }

    fun computePairingPin(pubA: ByteArray, pubB: ByteArray): String {
        val cmp = compareUnsigned(pubA, pubB)
        val minPub = if (cmp <= 0) pubA else pubB
        val maxPub = if (cmp <= 0) pubB else pubA

        val sha = MessageDigest.getInstance("SHA-256")
        sha.update("KC-PIN-V1".toByteArray(Charsets.UTF_8))
        sha.update(minPub)
        sha.update(maxPub)
        val digest = sha.digest()

        val rawU32 = ((digest[0].toLong() and 0xFFL) shl 24) or
            ((digest[1].toLong() and 0xFFL) shl 16) or
            ((digest[2].toLong() and 0xFFL) shl 8) or
            (digest[3].toLong() and 0xFFL)
        val pinNum = (rawU32 % 1_000_000L).toInt()
        return "%03d %03d".format(java.util.Locale.US, pinNum / 1000, pinNum % 1000)
    }

    fun deriveSessionKey(
        sharedSecret32: ByteArray,
        clientNonce16: ByteArray,
        hostNonce16: ByteArray,
    ): ByteArray {
        val salt = ByteArray(32)
        System.arraycopy(clientNonce16, 0, salt, 0, 16)
        System.arraycopy(hostNonce16, 0, salt, 16, 16)

        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(
            HKDFParameters(
                sharedSecret32,
                salt,
                "KeyConnect-Session-v1".toByteArray(Charsets.UTF_8)
            )
        )
        val out = ByteArray(32)
        hkdf.generateBytes(out, 0, 32)
        return out
    }

    private fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
        val minLen = minOf(a.size, b.size)
        for (i in 0 until minLen) {
            val ua = a[i].toInt() and 0xFF
            val ub = b[i].toInt() and 0xFF
            if (ua != ub) return ua.compareTo(ub)
        }
        return a.size.compareTo(b.size)
    }
}

object HexCodec {
    private val HEX_CHARS = "0123456789abcdef".toCharArray()

    fun encode(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            out[i * 2] = HEX_CHARS[v ushr 4]
            out[i * 2 + 1] = HEX_CHARS[v and 0x0F]
        }
        return String(out)
    }

    fun decode(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.length % 2 == 0) { "Hex string must have even length" }
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(clean[i * 2], 16)
            val lo = Character.digit(clean[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) { "Invalid hex character" }
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }
}
