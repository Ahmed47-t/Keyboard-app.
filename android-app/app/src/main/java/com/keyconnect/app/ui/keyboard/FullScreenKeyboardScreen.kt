package com.keyconnect.app.ui.keyboard

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.keyconnect.app.model.ConnectionUiState
import com.keyconnect.app.model.KeyboardLanguageLayer
import com.keyconnect.app.model.KeyboardSurfaceMode
import com.keyconnect.app.model.TransportMedium
import com.keyconnect.app.model.TransportPreference
import com.keyconnect.app.network.ConnectionOrchestrator
import com.keyconnect.app.ui.theme.AccentCyan
import com.keyconnect.app.ui.theme.AccentEmerald
import com.keyconnect.app.ui.theme.AccentIndigo
import com.keyconnect.app.ui.theme.BgDeep

@Composable
fun FullScreenKeyboardScreen(
    orchestrator: ConnectionOrchestrator,
    onBackToSetup: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val store = orchestrator.store
    val connState by orchestrator.connectionState.collectAsState()

    var surfaceMode by remember { mutableStateOf(store.surfaceMode) }
    var currentLayer by remember { mutableStateOf(store.defaultLanguage) }
    var showMediumMenu by remember { mutableStateOf(false) }

    BackHandler {
        orchestrator.releaseAllKeys()
        onBackToSetup()
    }

    // Enter True Immersive Full-Screen + Keep Screen Awake + Landscape Lock (when preferred)
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        val prevOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        if (activity != null && store.forceLandscapeKeyboard) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

        val controller = if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowInsetsControllerCompat(window, view).apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else null

        onDispose {
            orchestrator.releaseAllKeys()
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (activity != null && store.forceLandscapeKeyboard) {
                activity.requestedOrientation = prevOrientation
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {
        // Ultra-Slim 30dp Top Status & Mode Strip
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(Color(0xFF0B1120))
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Connection + Active Medium Selector Pill
                Box {
                    val (dotColor, statusLabel) = when (val s = connState) {
                        is ConnectionUiState.Connected -> {
                            val c = when (s.activeMedium) {
                                TransportMedium.USB -> AccentEmerald
                                TransportMedium.WIFI -> AccentCyan
                                TransportMedium.BLUETOOTH -> AccentIndigo
                                TransportMedium.WEB -> AccentCyan
                            }
                            c to "${s.hostName} • ${s.activeMedium.badge} (${s.rttMs}ms)"
                        }
                        is ConnectionUiState.Connecting -> AccentCyan to "يتصل عبر ${s.medium.badge}..."
                        else -> Color(0xFFEF4444) to "غير متصل — اضغط للإعداد"
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (connState is ConnectionUiState.Connected) {
                                    showMediumMenu = true
                                } else {
                                    onBackToSetup()
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = statusLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                    }

                    DropdownMenu(
                        expanded = showMediumMenu,
                        onDismissRequest = { showMediumMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("تلقائي ذكي (USB ← Wi-Fi ← BT)") },
                            onClick = {
                                showMediumMenu = false
                                orchestrator.switchTransportPreference(TransportPreference.AUTO_SMART)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("⚡ سلك USB فقط") },
                            onClick = {
                                showMediumMenu = false
                                orchestrator.switchTransportPreference(TransportPreference.USB_ONLY)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("📶 شبكة Wi-Fi فقط") },
                            onClick = {
                                showMediumMenu = false
                                orchestrator.switchTransportPreference(TransportPreference.WIFI_ONLY)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("🔵 بلوتوث مباشر فقط") },
                            onClick = {
                                showMediumMenu = false
                                orchestrator.switchTransportPreference(TransportPreference.BLUETOOTH_ONLY)
                            }
                        )
                    }
                }

                // Mode Switcher + Settings Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Toggle between Full PC Keyboard & Phone Native Keyboard / Voice
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = Color(0xFF1E293B),
                        modifier = Modifier.clickable {
                            val next = if (surfaceMode == KeyboardSurfaceMode.PC_FULL_CUSTOM) {
                                KeyboardSurfaceMode.SYSTEM_IME
                            } else {
                                KeyboardSurfaceMode.PC_FULL_CUSTOM
                            }
                            surfaceMode = next
                            store.surfaceMode = next
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (surfaceMode == KeyboardSurfaceMode.PC_FULL_CUSTOM) {
                                    Icons.Default.Mic
                                } else {
                                    Icons.Default.Keyboard
                                },
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (surfaceMode == KeyboardSurfaceMode.PC_FULL_CUSTOM) {
                                    "كيبورد الهاتف / صوتي"
                                } else {
                                    "كيبورد PC كامل"
                                },
                                fontSize = 10.5.sp,
                                color = Color.White
                            )
                        }
                    }

                    // Setup Button
                    Surface(
                        shape = RoundedCornerShape(7.dp),
                        color = Color(0xFF1E293B),
                        modifier = Modifier.clickable { onBackToSetup() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "إعدادات",
                                tint = Color(0xFFCBD5E1),
                                modifier = Modifier.size(13.dp)
                            )
                            Text("الإعداد", fontSize = 10.5.sp, color = Color(0xFFCBD5E1))
                        }
                    }
                }
            }
        }

        // Remaining 93%+ of Screen = Full Edge-to-Edge Keyboard
        if (surfaceMode == KeyboardSurfaceMode.PC_FULL_CUSTOM) {
            PcFullKeyboard(
                orchestrator = orchestrator,
                currentLayer = currentLayer,
                hapticsEnabled = store.hapticsEnabled,
                onLayerChange = { nextLayer ->
                    currentLayer = nextLayer
                    if (nextLayer != KeyboardLanguageLayer.FN_NUMPAD) {
                        store.defaultLanguage = nextLayer
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            SystemImeKeyboardMode(
                orchestrator = orchestrator,
                hapticsEnabled = store.hapticsEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}
