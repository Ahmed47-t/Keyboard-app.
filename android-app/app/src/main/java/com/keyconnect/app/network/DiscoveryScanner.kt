package com.keyconnect.app.network

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.keyconnect.app.crypto.StaticKeyIdentity
import com.keyconnect.app.model.DiscoveredHost
import com.keyconnect.app.model.DiscoveryPacket
import com.keyconnect.app.model.ProtocolConstants
import com.keyconnect.app.model.TrustedHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

class DiscoveryScanner(
    private val context: Context,
    private val identity: StaticKeyIdentity,
    private val deviceNameProvider: () -> String,
    private val trustedHostsProvider: () -> List<TrustedHost>,
    private val onHostsUpdated: (List<DiscoveredHost>) -> Unit,
) {
    private val discoveredMap = LinkedHashMap<String, DiscoveredHost>()
    private var scanJob: Job? = null

    fun start(scope: CoroutineScope) {
        if (scanJob?.isActive == true) return
        scanJob = scope.launch(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket().apply {
                    broadcast = true
                    soTimeout = 450
                }

                // Listener coroutine for UDP replies & host beacons on ephemeral + discovery port
                val recvBuf = ByteArray(2048)
                while (isActive) {
                    // 1. Broadcast KC_DISCOVER_V1 across Wi-Fi LAN, Hotspot & USB RNDIS interfaces
                    val discoverBytes = ProtocolConstants.json.encodeToString<DiscoveryPacket>(
                        DiscoveryPacket.KcDiscoverV1(
                            deviceId = identity.deviceId,
                            deviceName = deviceNameProvider(),
                            publicKeyHex = identity.publicHex,
                        )
                    ).toByteArray(Charsets.UTF_8)

                    for (bcast in collectBroadcastTargets()) {
                        runCatching {
                            socket.send(
                                DatagramPacket(
                                    discoverBytes,
                                    discoverBytes.size,
                                    bcast,
                                    ProtocolConstants.UDP_DISCOVERY_PORT
                                )
                            )
                        }
                    }

                    // Drain incoming UDP announce replies within window
                    val deadline = System.currentTimeMillis() + 900L
                    while (System.currentTimeMillis() < deadline && isActive) {
                        val pkt = DatagramPacket(recvBuf, recvBuf.size)
                        val received = runCatching {
                            socket.receive(pkt)
                            true
                        }.getOrDefault(false)
                        if (!received) break

                        val jsonStr = String(pkt.data, 0, pkt.length, Charsets.UTF_8)
                        val parsed = runCatching {
                            ProtocolConstants.json.decodeFromString(
                                DiscoveryPacket.serializer(),
                                jsonStr
                            )
                        }.getOrNull()

                        if (parsed is DiscoveryPacket.KcAnnounceV1) {
                            val senderIp = pkt.address.hostAddress ?: continue
                            val isUsbRndis = isUsbTetherSubnet(senderIp)
                            registerAnnounce(parsed, senderIp, isUsbRndis)
                        }
                    }

                    // 2. Probe 127.0.0.1:45821 for physical USB ADB Reverse Tunnel
                    val adbLoopbackOpen = probeTcpPort("127.0.0.1", ProtocolConstants.TCP_CONTROL_PORT, 180)
                    updateUsbLoopbackHost(adbLoopbackOpen)

                    // 3. Merge Bonded Bluetooth Classic Windows Laptops
                    mergeBluetoothBondedComputers()

                    publishMergedList()
                    delay(1800L)
                }
            } finally {
                runCatching { socket?.close() }
            }
        }
    }

    fun stop() {
        scanJob?.cancel()
        scanJob = null
    }

    @Synchronized
    private fun registerAnnounce(
        announce: DiscoveryPacket.KcAnnounceV1,
        ip: String,
        isUsbRndis: Boolean,
    ) {
        val trustedIds = trustedHostsProvider().map { it.deviceId }.toSet()
        val existing = discoveredMap[announce.deviceId]
        val updated = DiscoveredHost(
            deviceId = announce.deviceId,
            deviceName = announce.deviceName,
            publicKeyHex = announce.publicKeyHex,
            wifiIp = if (isUsbRndis) existing?.wifiIp else ip,
            usbAvailable = isUsbRndis || (existing?.usbAvailable == true),
            usbIp = if (isUsbRndis) ip else existing?.usbIp,
            bluetoothAddress = existing?.bluetoothAddress,
            tcpPort = announce.tcpPort,
            udpFastPort = announce.udpFastPort,
            isTrusted = announce.deviceId in trustedIds || announce.isPaired,
            lastSeenMs = System.currentTimeMillis(),
        )
        discoveredMap[announce.deviceId] = updated
    }

    @Synchronized
    private fun updateUsbLoopbackHost(loopbackOpen: Boolean) {
        val trusted = trustedHostsProvider()
        if (loopbackOpen) {
            val primaryTrusted = trusted.firstOrNull()
            val keyId = primaryTrusted?.deviceId ?: "usb-adb-direct"
            val existing = discoveredMap[keyId]
            discoveredMap[keyId] = DiscoveredHost(
                deviceId = keyId,
                deviceName = primaryTrusted?.deviceName ?: existing?.deviceName ?: "حاسوب متصل عبر سلك USB",
                publicKeyHex = primaryTrusted?.publicKeyHex ?: existing?.publicKeyHex ?: "",
                wifiIp = existing?.wifiIp,
                usbAvailable = true,
                usbIp = "127.0.0.1",
                bluetoothAddress = existing?.bluetoothAddress ?: primaryTrusted?.bluetoothAddress,
                isTrusted = primaryTrusted != null,
                lastSeenMs = System.currentTimeMillis(),
            )
        } else {
            val adbOnly = discoveredMap["usb-adb-direct"]
            if (adbOnly != null && adbOnly.wifiIp == null) {
                discoveredMap.remove("usb-adb-direct")
            }
        }
    }

    @SuppressLint("MissingPermission")
    @Synchronized
    private fun mergeBluetoothBondedComputers() {
        if (!hasBluetoothConnectPermission()) return
        val btManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return
        val adapter = btManager.adapter ?: return
        if (!adapter.isEnabled) return

        val trustedByBt = trustedHostsProvider().associateBy { it.bluetoothAddress }
        val bonded = runCatching { adapter.bondedDevices }.getOrNull() ?: return

        for (device in bonded) {
            val major = device.bluetoothClass?.majorDeviceClass
            val isComputerOrUnknown = major == BluetoothClass.Device.Major.COMPUTER ||
                major == BluetoothClass.Device.Major.UNCATEGORIZED ||
                trustedByBt.containsKey(device.address)

            if (!isComputerOrUnknown) continue

            val matchedTrusted = trustedByBt[device.address]
                ?: trustedHostsProvider().firstOrNull {
                    it.deviceName.equals(device.name, ignoreCase = true)
                }

            val id = matchedTrusted?.deviceId ?: "bt-${device.address}"
            val existing = discoveredMap[id]
            discoveredMap[id] = DiscoveredHost(
                deviceId = id,
                deviceName = matchedTrusted?.deviceName ?: device.name ?: device.address,
                publicKeyHex = matchedTrusted?.publicKeyHex ?: existing?.publicKeyHex ?: "",
                wifiIp = existing?.wifiIp,
                usbAvailable = existing?.usbAvailable ?: false,
                usbIp = existing?.usbIp,
                bluetoothAddress = device.address,
                isTrusted = matchedTrusted != null,
                lastSeenMs = System.currentTimeMillis(),
            )
        }
    }

    @Synchronized
    private fun publishMergedList() {
        val now = System.currentTimeMillis()
        // Expire stale dynamic Wi-Fi entries older than 12s unless trusted or Bluetooth-backed
        val iter = discoveredMap.entries.iterator()
        while (iter.hasNext()) {
            val entry = iter.next().value
            if (now - entry.lastSeenMs > 12_000L && !entry.isTrusted && entry.bluetoothAddress == null) {
                iter.remove()
            }
        }

        // Ensure all saved trusted hosts appear even when offline so user can one-tap reconnect
        for (trusted in trustedHostsProvider()) {
            if (!discoveredMap.containsKey(trusted.deviceId)) {
                discoveredMap[trusted.deviceId] = DiscoveredHost(
                    deviceId = trusted.deviceId,
                    deviceName = trusted.deviceName,
                    publicKeyHex = trusted.publicKeyHex,
                    wifiIp = trusted.lastKnownIps.firstOrNull(),
                    bluetoothAddress = trusted.bluetoothAddress,
                    tcpPort = trusted.tcpPort,
                    udpFastPort = trusted.udpFastPort,
                    isTrusted = true,
                    lastSeenMs = 0L,
                )
            }
        }

        val sorted = discoveredMap.values.sortedWith(
            compareByDescending<DiscoveredHost> { it.usbAvailable }
                .thenByDescending { it.isTrusted }
                .thenByDescending { it.wifiIp != null }
        )
        onHostsUpdated(sorted)
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun collectBroadcastTargets(): List<InetAddress> {
        val result = linkedSetOf<InetAddress>()
        runCatching { result.add(InetAddress.getByName("255.255.255.255")) }
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val nif = interfaces.nextElement()
                if (!nif.isUp || nif.isLoopback) continue
                for (ia in nif.interfaceAddresses) {
                    val bcast = ia.broadcast
                    if (bcast is Inet4Address) {
                        result.add(bcast)
                    }
                }
            }
        }
        return result.toList()
    }

    private fun isUsbTetherSubnet(ip: String): Boolean =
        ip.startsWith("192.168.42.") ||
            ip.startsWith("192.168.44.") ||
            ip.startsWith("192.168.136.")

    private fun probeTcpPort(host: String, port: Int, timeoutMs: Int): Boolean {
        return runCatching {
            Socket().use { s ->
                s.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        }.getOrDefault(false)
    }
}
