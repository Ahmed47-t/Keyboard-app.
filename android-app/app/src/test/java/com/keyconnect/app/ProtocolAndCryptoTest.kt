package com.keyconnect.app

import com.keyconnect.app.crypto.CipherDirection
import com.keyconnect.app.crypto.HexCodec
import com.keyconnect.app.crypto.KeyConnectCrypto
import com.keyconnect.app.crypto.SessionAeadCipher
import com.keyconnect.app.crypto.StaticKeyIdentity
import com.keyconnect.app.model.ClientMessage
import com.keyconnect.app.model.DiscoveryPacket
import com.keyconnect.app.model.ProtocolConstants
import com.keyconnect.app.model.TransportMedium
import com.keyconnect.app.model.WireFrame
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolAndCryptoTest {

    @Test
    fun `matches Rust Host RFC 7748 + HKDF + ChaCha20-Poly1305 vectors`() {
        val clientSeed = HexCodec.decode("0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20")
        val hostSeed = HexCodec.decode("2122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f40")

        val client = StaticKeyIdentity.fromSecretSeed(clientSeed)
        val host = StaticKeyIdentity.fromSecretSeed(hostSeed)

        assertEquals(
            "07a37cbc142093c8b755dc1b10e86cb426374ad16aa853ed0bdfc0b2b86d1c7c",
            client.publicHex
        )
        assertEquals(
            "5869aff450549732cbaaed5e5df9b30a6da31cb0e5742bad5ad4a1a768f1a67b",
            host.publicHex
        )
        assertEquals("aaa8fff703b50b22", client.deviceId)
        assertEquals("4457134794559182", host.deviceId)

        val shared = client.diffieHellman(host.publicBytes)
        assertEquals(
            "a84dc7c3c8f058b1b2dc4cd1e9b5dc0a7987f88b6a9564cde3391fc421159e77",
            HexCodec.encode(shared)
        )

        val pin = KeyConnectCrypto.computePairingPin(client.publicBytes, host.publicBytes)
        assertEquals("143 368", pin)

        val clientNonce = HexCodec.decode("a0a1a2a3a4a5a6a7a8a9aaabacadaeaf")
        val hostNonce = HexCodec.decode("b0b1b2b3b4b5b6b7b8b9babbbcbdbebf")
        val sessionKey = KeyConnectCrypto.deriveSessionKey(shared, clientNonce, hostNonce)
        assertEquals(
            "1edf368ddd81734f4334ef64b7b646e8b2d8dd55cfdcfdfabdf157b494c29c2e",
            HexCodec.encode(sessionKey)
        )

        val cipher = SessionAeadCipher(sessionKey, "KCSESS01".toByteArray(Charsets.UTF_8))
        val msg: ClientMessage = ClientMessage.TextCommit(
            eventId = 1L,
            text = "السلام عليكم Keyboard!"
        )
        val jsonBytes = ProtocolConstants.json.encodeToString(msg).toByteArray(Charsets.UTF_8)
        val sealed = cipher.seal(CipherDirection.CLIENT_TO_HOST, 1L, jsonBytes)

        assertArrayEquals(
            HexCodec.decode(
                "3b6c617cec95e1888fc653dadb4f6c6fae8bc2f37002e401fe2edb4de46e8e1c0f550bcd475a236c7f3ee82d960ac07687e600bdcc45fe6b5598af27a5287e41972d9eb5f6b862714864efda7d08c484743b955750cf7768a9715fe8fa3b"
            ),
            sealed
        )
    }

    @Test
    fun `wire json discriminators match Rust serde format`() {
        val discover: DiscoveryPacket = DiscoveryPacket.KcDiscoverV1(
            deviceId = "aaa8fff703b50b22",
            deviceName = "Pixel 8",
            publicKeyHex = "07a3"
        )
        val discoverJson = ProtocolConstants.json.encodeToString(discover)
        assertTrue(discoverJson.contains("\"packet_type\":\"KC_DISCOVER_V1\""))

        val hello: WireFrame = WireFrame.ClientHello(
            deviceId = "aaa8fff703b50b22",
            deviceName = "Pixel 8",
            publicKeyHex = "07a3",
            clientNonceHex = "a0a1",
            medium = TransportMedium.USB
        )
        val helloJson = ProtocolConstants.json.encodeToString(hello)
        assertTrue(helloJson.contains("\"frame\":\"client_hello\""))
        assertTrue(helloJson.contains("\"medium\":\"usb\""))
    }
}
