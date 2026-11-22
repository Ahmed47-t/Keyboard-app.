package com.keyconnect.app.network

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.Uri
import com.keyconnect.app.model.ClientMessage
import com.keyconnect.app.model.ConnectionUiState
import com.keyconnect.app.model.DiscoveredHost
import com.keyconnect.app.model.KeyAction
import com.keyconnect.app.model.ModifiersState
import com.keyconnect.app.model.ProtocolConstants
import com.keyconnect.app.model.TransportMedium
import com.keyconnect.app.model.TransportPreference
import com.keyconnect.app.model.TrustedHost
import com.keyconnect.app.storage.TrustedHostStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class ConnectionOrchestrator(
    private val appContext: Context,
    val store: TrustedHostStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val identity = store.getOrCreateIdentity()

    private val _hosts = MutableStateFlow<List<DiscoveredHost>>(emptyList())
    val hosts: StateFlow<List<DiscoveredHost>> = _hosts.asStateFlow()

    private val _connectionState = MutableStateFlow<ConnectionUiState>(ConnectionUiState.Disconnected)
    val connectionState: StateFlow<ConnectionUiState> = _connectionState.asStateFlow()

    private val activeLinks = ConcurrentHashMap<TransportMedium, StreamLinkSession>()
    private val inFlightMessages = ArrayDeque<Pair<Long, ClientMessage>>(64)
    private val eventIdCounter = AtomicLong(0L)
    private val outboundQueue = Channel<ClientMessage>(capacity = 256)

    private var currentTargetHost: DiscoveredHost? = null
    private var failoverMonitorJob: Job? = null
    private val btHidController = BluetoothHidController(appContext)

    private val scanner = DiscoveryScanner(
        context = appContext,
        identity = identity,
        deviceNameProvider = { store.deviceName },
        trustedHostsProvider = { store.loadTrustedHosts() },
        onHostsUpdated = { updated -> _hosts.value = updated }
    )

    init {
        scanner.start(scope)
        startSenderLoop()
    }

    fun startDiscovery() {
        scanner.start(scope)
    }

    /**
     * Parses `keyconnect://pair?id=...&name=...&ips=...&port=...&pub=...&token=...`
     * scanned from the Windows Host QR Code and pairs immediately.
     */
    fun connectViaQrUri(rawUri: String, onPairedSuccess: () -> Unit) {
        val uri = runCatching { Uri.parse(rawUri.trim()) }.getOrNull() ?: return
        if (uri.scheme != "keyconnect" || uri.host != "pair") {
            _connectionState.value = ConnectionUiState.Error("رمز QR غير صالح لتطبيق KeyConnect")
            return
        }

        val id = uri.getQueryParameter("id") ?: return
        val name = uri.getQueryParameter("name")?.let {
            runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it)
        } ?: "Windows PC"
        val ipsParam = uri.getQueryParameter("ips") ?: ""
        val port = uri.getQueryParameter("port")?.toIntOrNull() ?: ProtocolConstants.TCP_CONTROL_PORT
        val pubHex = uri.getQueryParameter("pub") ?: ""
        val token = uri.getQueryParameter("token")

        val ips = ipsParam.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val usbIp = ips.firstOrNull { it == "127.0.0.1" || it.startsWith("192.168.42.") }
        val wifiIp = ips.firstOrNull { it != "127.0.0.1" && !it.startsWith("192.168.42.") }

        val target = DiscoveredHost(
            deviceId = id,
            deviceName = name,
            publicKeyHex = pubHex,
            wifiIp = wifiIp,
            usbAvailable = usbIp != null,
            usbIp = usbIp ?: "127.0.0.1",
            tcpPort = port,
            isTrusted = true,
        )

        connectToHost(target, qrPairingToken = token, onConnected = onPairedSuccess)
    }

    fun connectByManualIp(ip: String, onConnected: () -> Unit = {}) {
        val cleanIp = ip.trim()
        if (cleanIp.isEmpty()) return
        val isUsb = cleanIp == "127.0.0.1" || cleanIp.startsWith("192.168.42.")
        val target = DiscoveredHost(
            deviceId = "manual-$cleanIp",
            deviceName = "Windows ($cleanIp)",
            publicKeyHex = "",
            wifiIp = if (isUsb) null else cleanIp,
            usbAvailable = isUsb,
            usbIp = if (isUsb) cleanIp else null,
        )
        connectToHost(target, qrPairingToken = null, onConnected = onConnected)
    }

    fun connectToHost(
        host: DiscoveredHost,
        qrPairingToken: String? = null,
        onConnected: () -> Unit = {},
    ) {
        val pref = store.transportPreference
        if (pref == TransportPreference.BLUETOOTH_HID) {
            _connectionState.value = ConnectionUiState.Connecting(host.deviceName, TransportMedium.BLUETOOTH)
            btHidController.startHidProfile(host.bluetoothAddress) { ok, info ->
                scope.launch {
                    if (ok) {
                        _connectionState.value = ConnectionUiState.Connected(
                            hostId = host.deviceId,
                            hostName = info,
                            activeMedium = TransportMedium.BLUETOOTH,
                            standbyMediums = emptySet(),
                            rttMs = 4L,
                            sentEventsCount = eventIdCounter.get()
                        )
                        onConnected()
                    } else {
                        _connectionState.value = ConnectionUiState.Error(info)
                    }
                }
            }
            return
        }

        currentTargetHost = host
        scope.launch {
            disconnectAllInternal(resetUi = false)

            val candidateOrder = buildCandidateOrder(host, pref)
            if (candidateOrder.isEmpty()) {
                _connectionState.value = ConnectionUiState.Error(
                    "لا توجد وسيلة اتصال متاحة حالياً لهذا الحاسوب (تأكد من Wi-Fi أو سلك USB أو البلوتوث)"
                )
                return@launch
            }

            var establishedPrimary: StreamLinkSession? = null
            var lastError = "تعذر الاتصال بالحاسوب"

            for (medium in candidateOrder) {
                _connectionState.value = ConnectionUiState.Connecting(host.deviceName, medium)
                val res = openMediumLink(host, medium, qrPairingToken)
                res.onSuccess { session ->
                    activeLinks[medium] = session
                    if (establishedPrimary == null) {
                        establishedPrimary = session
                    }
                }.onFailure { err ->
                    lastError = err.message ?: lastError
                }

                // Once primary link is up, immediately transition user to keyboard and warm standby links in background!
                if (establishedPrimary != null) break
            }

            val primary = establishedPrimary
            if (primary == null) {
                _connectionState.value = ConnectionUiState.Error(lastError)
                return@launch
            }

            // Persist trusted host record after verified handshake
            store.saveTrustedHost(
                TrustedHost(
                    deviceId = primary.hostDeviceId.ifEmpty { host.deviceId },
                    deviceName = primary.hostDeviceName.ifEmpty { host.deviceName },
                    publicKeyHex = primary.hostPublicKeyHex.ifEmpty { host.publicKeyHex },
                    lastKnownIps = listOfNotNull(host.wifiIp),
                    bluetoothAddress = host.bluetoothAddress,
                    tcpPort = host.tcpPort,
                    udpFastPort = host.udpFastPort,
                )
            )

            refreshConnectedUiState()
            onConnected()

            // Start smart auto-failover supervisor (warms standby links & promotes USB automatically)
            if (pref == TransportPreference.AUTO_SMART) {
                startFailoverSupervisor(host)
            }
        }
    }

    /**
     * Allows user to switch preferred transport medium on the fly from the Keyboard top bar.
     */
    fun switchTransportPreference(pref: TransportPreference) {
        store.transportPreference = pref
        val target = currentTargetHost ?: return
        connectToHost(target)
    }

    fun sendKeyEvent(action: KeyAction, key: String, modifiers: ModifiersState = ModifiersState()) {
        if (store.transportPreference == TransportPreference.BLUETOOTH_HID && btHidController.isConnected) {
            btHidController.sendHidKeyEvent(action, key, modifiers)
            eventIdCounter.incrementAndGet()
            refreshConnectedUiState()
            return
        }
        val id = eventIdCounter.incrementAndGet()
        enqueueOutbound(
            ClientMessage.KeyEvent(
                eventId = id,
                action = action,
                key = key,
                modifiers = modifiers
            )
        )
    }

    fun sendUnicodeText(text: String) {
        if (text.isEmpty()) return
        if (store.transportPreference == TransportPreference.BLUETOOTH_HID && btHidController.isConnected) {
            for (ch in text) {
                btHidController.sendHidKeyEvent(KeyAction.PRESS, ch.toString(), ModifiersState())
            }
            return
        }
        val id = eventIdCounter.incrementAndGet()
        enqueueOutbound(ClientMessage.TextCommit(eventId = id, text = text))
    }

    fun sendShortcutCombo(modifiers: List<String>, key: String) {
        val id = eventIdCounter.incrementAndGet()
        enqueueOutbound(
            ClientMessage.ShortcutCombo(
                eventId = id,
                modifiers = modifiers,
                key = key
            )
        )
    }

    fun releaseAllKeys() {
        val id = eventIdCounter.incrementAndGet()
        enqueueOutbound(ClientMessage.ReleaseAll(eventId = id))
    }

    fun disconnect() {
        scope.launch {
            currentTargetHost = null
            btHidController.stop()
            disconnectAllInternal(resetUi = true)
        }
    }

    private fun enqueueOutbound(msg: ClientMessage) {
        outboundQueue.trySend(msg)
    }

    private fun startSenderLoop() {
        scope.launch(Dispatchers.IO) {
            for (msg in outboundQueue) {
                val eventId = extractEventId(msg)
                if (eventId > 0L) {
                    synchronized(inFlightMessages) {
                        if (inFlightMessages.size >= 48) inFlightMessages.removeFirst()
                        inFlightMessages.addLast(eventId to msg)
                    }
                }

                val bestLink = pickBestAliveLink()
                if (bestLink != null) {
                    val useFastUdp = bestLink.medium == TransportMedium.WIFI && msg is ClientMessage.KeyEvent
                    val sent = bestLink.sendClientMessage(msg, preferFastUdp = useFastUdp)
                    if (!sent) {
                        // Immediate failover to next standby link!
                        val fallback = pickBestAliveLink()
                        fallback?.sendEncryptedReliable(msg)
                    }
                    refreshConnectedUiState()
                }
            }
        }
    }

    private fun startFailoverSupervisor(host: DiscoveredHost) {
        failoverMonitorJob?.cancel()
        failoverMonitorJob = scope.launch(Dispatchers.IO) {
            while (isActive && currentTargetHost?.deviceId == host.deviceId) {
                // Keep latest discovered IPs merged for this host
                val latest = _hosts.value.firstOrNull { it.deviceId == host.deviceId } ?: host

                for (medium in listOf(TransportMedium.USB, TransportMedium.WIFI, TransportMedium.BLUETOOTH)) {
                    val existing = activeLinks[medium]
                    if (existing != null && existing.isAlive) continue

                    val canTry = when (medium) {
                        TransportMedium.USB -> true // Always probe 127.0.0.1 ADB tunnel + USB RNDIS!
                        TransportMedium.WIFI -> latest.wifiIp != null
                        TransportMedium.BLUETOOTH -> latest.bluetoothAddress != null && activeLinks.isEmpty()
                        TransportMedium.WEB -> false
                    }
                    if (!canTry) continue

                    openMediumLink(latest, medium, qrPairingToken = null).onSuccess { link ->
                        activeLinks[medium] = link
                        replayUnacknowledged(link)
                        refreshConnectedUiState()
                    }
                }

                refreshConnectedUiState()
                delay(2500L)
            }
        }
    }

    private suspend fun openMediumLink(
        host: DiscoveredHost,
        medium: TransportMedium,
        qrPairingToken: String?,
    ): Result<StreamLinkSession> = withContext(Dispatchers.IO) {
        runCatching {
            val session = when (medium) {
                TransportMedium.USB -> {
                    val usbTargetIp = host.usbIp ?: "127.0.0.1"
                    val sock = Socket()
                    sock.tcpNoDelay = true
                    sock.connect(InetSocketAddress(usbTargetIp, host.tcpPort), 750)
                    StreamLinkSession(
                        medium = TransportMedium.USB,
                        rawInput = sock.getInputStream(),
                        rawOutput = sock.getOutputStream(),
                        underlyingCloseable = sock,
                    )
                }

                TransportMedium.WIFI -> {
                    val ip = host.wifiIp ?: error("عنوان IP لشبكة Wi-Fi غير معروف")
                    val sock = Socket()
                    sock.tcpNoDelay = true
                    sock.connect(InetSocketAddress(ip, host.tcpPort), 2200)
                    val fastTarget = InetAddress.getByName(ip) to host.udpFastPort
                    StreamLinkSession(
                        medium = TransportMedium.WIFI,
                        rawInput = sock.getInputStream(),
                        rawOutput = sock.getOutputStream(),
                        underlyingCloseable = sock,
                        udpFastTarget = fastTarget,
                    )
                }

                TransportMedium.BLUETOOTH -> openBluetoothRfcommSession(host)
                TransportMedium.WEB -> error("Web transport not used on native client")
            }

            session.performHandshake(
                clientIdentity = identity,
                clientDeviceName = store.deviceName,
                qrPairingToken = qrPairingToken,
                onPinVerificationRequired = { hostName, pin ->
                    _connectionState.value = ConnectionUiState.AwaitingPairingPin(hostName, pin, medium)
                }
            ).getOrThrow()

            session.startBackgroundLoops(
                scope = scope,
                onAckReceived = { ackId ->
                    synchronized(inFlightMessages) {
                        while (inFlightMessages.isNotEmpty() && inFlightMessages.first().first <= ackId) {
                            inFlightMessages.removeFirst()
                        }
                    }
                },
                onDisconnected = { droppedMedium ->
                    activeLinks.remove(droppedMedium)
                    val replacement = pickBestAliveLink()
                    if (replacement != null) {
                        scope.launch(Dispatchers.IO) {
                            replayUnacknowledged(replacement)
                            refreshConnectedUiState()
                        }
                    } else {
                        refreshConnectedUiState()
                    }
                }
            )
            session
        }
    }

    @SuppressLint("MissingPermission")
    private fun openBluetoothRfcommSession(host: DiscoveredHost): StreamLinkSession {
        val addr = host.bluetoothAddress ?: error("لا يوجد عنوان بلوتوث مقترن لهذا الحاسوب")
        val btManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: error("البلوتوث غير متاح")
        val adapter = btManager.adapter ?: error("محول البلوتوث غير متاح")
        runCatching { adapter.cancelDiscovery() }
        val device = adapter.getRemoteDevice(addr)
        val btSocket = device.createRfcommSocketToServiceRecord(
            UUID.fromString(ProtocolConstants.BT_SERVICE_UUID)
        )
        btSocket.connect()
        return StreamLinkSession(
            medium = TransportMedium.BLUETOOTH,
            rawInput = btSocket.inputStream,
            rawOutput = btSocket.outputStream,
            underlyingCloseable = btSocket,
        )
    }

    private suspend fun replayUnacknowledged(targetLink: StreamLinkSession) {
        val pending = synchronized(inFlightMessages) { inFlightMessages.toList() }
        for ((_, msg) in pending) {
            targetLink.sendEncryptedReliable(msg)
        }
    }

    private fun pickBestAliveLink(): StreamLinkSession? {
        return activeLinks.values
            .filter { it.isAlive }
            .maxByOrNull { it.medium.priorityScore }
    }

    private fun refreshConnectedUiState() {
        val best = pickBestAliveLink()
        if (best != null) {
            val standbys = activeLinks.keys.filter { it != best.medium && activeLinks[it]?.isAlive == true }.toSet()
            _connectionState.value = ConnectionUiState.Connected(
                hostId = best.hostDeviceId.ifEmpty { currentTargetHost?.deviceId.orEmpty() },
                hostName = best.hostDeviceName.ifEmpty { currentTargetHost?.deviceName ?: "Windows PC" },
                activeMedium = best.medium,
                standbyMediums = standbys,
                rttMs = best.latestRttMs,
                sentEventsCount = eventIdCounter.get(),
            )
        } else if (currentTargetHost != null) {
            _connectionState.value = ConnectionUiState.Connecting(
                currentTargetHost?.deviceName ?: "Windows PC",
                TransportMedium.WIFI
            )
        } else {
            _connectionState.value = ConnectionUiState.Disconnected
        }
    }

    private fun buildCandidateOrder(
        host: DiscoveredHost,
        pref: TransportPreference,
    ): List<TransportMedium> {
        return when (pref) {
            TransportPreference.USB_ONLY -> listOf(TransportMedium.USB)
            TransportPreference.WIFI_ONLY -> listOfNotNull(host.wifiIp?.let { TransportMedium.WIFI })
            TransportPreference.BLUETOOTH_ONLY -> listOfNotNull(
                host.bluetoothAddress?.let { TransportMedium.BLUETOOTH }
            )
            TransportPreference.BLUETOOTH_HID -> listOf(TransportMedium.BLUETOOTH)
            TransportPreference.AUTO_SMART -> buildList {
                // Always try USB first (fastest <0.5ms), then Wi-Fi, then Bluetooth
                add(TransportMedium.USB)
                if (host.wifiIp != null) add(TransportMedium.WIFI)
                if (host.bluetoothAddress != null) add(TransportMedium.BLUETOOTH)
            }
        }
    }

    private fun extractEventId(msg: ClientMessage): Long = when (msg) {
        is ClientMessage.KeyEvent -> msg.eventId
        is ClientMessage.TextCommit -> msg.eventId
        is ClientMessage.ShortcutCombo -> msg.eventId
        is ClientMessage.ReleaseAll -> msg.eventId
        is ClientMessage.Ping -> 0L
    }

    private suspend fun disconnectAllInternal(resetUi: Boolean) {
        failoverMonitorJob?.cancel()
        val sessions = activeLinks.values.toList()
        activeLinks.clear()
        for (s in sessions) {
            s.shutdown()
        }
        if (resetUi) {
            _connectionState.value = ConnectionUiState.Disconnected
        }
    }
}
