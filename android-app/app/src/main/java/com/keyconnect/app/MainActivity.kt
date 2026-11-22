package com.keyconnect.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.keyconnect.app.model.ConnectionUiState
import com.keyconnect.app.network.ConnectionOrchestrator
import com.keyconnect.app.storage.TrustedHostStore
import com.keyconnect.app.ui.keyboard.FullScreenKeyboardScreen
import com.keyconnect.app.ui.setup.SetupScreen
import com.keyconnect.app.ui.theme.KeyConnectTheme

class MainActivity : ComponentActivity() {

    private lateinit var orchestrator: ConnectionOrchestrator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val store = TrustedHostStore(applicationContext)
        orchestrator = ConnectionOrchestrator(applicationContext, store)

        setContent {
            KeyConnectTheme {
                var showFullKeyboard by rememberSaveable { mutableStateOf(false) }
                val hosts by orchestrator.hosts.collectAsState()
                val connState by orchestrator.connectionState.collectAsState()
                var attemptedAutoReconnect by rememberSaveable { mutableStateOf(false) }

                // Seamless KDE Connect-style auto-reconnect to last trusted laptop on launch
                LaunchedEffect(hosts) {
                    if (!attemptedAutoReconnect && connState is ConnectionUiState.Disconnected) {
                        val lastId = store.lastConnectedHostId
                        val match = hosts.firstOrNull {
                            it.isTrusted && (it.deviceId == lastId || lastId == null) &&
                                (it.usbAvailable || it.wifiIp != null || it.bluetoothAddress != null)
                        }
                        if (match != null) {
                            attemptedAutoReconnect = true
                            orchestrator.connectToHost(match) {
                                if (store.autoOpenKeyboardOnConnect) {
                                    showFullKeyboard = true
                                }
                            }
                        }
                    }
                }

                if (showFullKeyboard) {
                    FullScreenKeyboardScreen(
                        orchestrator = orchestrator,
                        onBackToSetup = { showFullKeyboard = false }
                    )
                } else {
                    SetupScreen(
                        orchestrator = orchestrator,
                        onOpenFullScreenKeyboard = { showFullKeyboard = true }
                    )
                }
            }
        }
    }
}
