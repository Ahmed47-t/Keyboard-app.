package com.keyconnect.app.ui.setup

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keyconnect.app.model.ConnectionUiState
import com.keyconnect.app.model.DiscoveredHost
import com.keyconnect.app.model.TransportMedium
import com.keyconnect.app.model.TransportPreference
import com.keyconnect.app.network.ConnectionOrchestrator
import com.keyconnect.app.ui.theme.AccentCyan
import com.keyconnect.app.ui.theme.AccentEmerald
import com.keyconnect.app.ui.theme.AccentIndigo

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(
    orchestrator: ConnectionOrchestrator,
    onOpenFullScreenKeyboard: () -> Unit,
) {
    val hosts by orchestrator.hosts.collectAsState()
    val connState by orchestrator.connectionState.collectAsState()
    val store = orchestrator.store

    var selectedTransportPref by remember { mutableStateOf(store.transportPreference) }
    var hapticsEnabled by remember { mutableStateOf(store.hapticsEnabled) }
    var forceLandscape by remember { mutableStateOf(store.forceLandscapeKeyboard) }
    var autoOpenKeyboard by remember { mutableStateOf(store.autoOpenKeyboardOnConnect) }

    var showQrScanner by remember { mutableStateOf(false) }
    var showManualIpDialog by remember { mutableStateOf(false) }
    var manualIpInput by remember { mutableStateOf("") }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        orchestrator.startDiscovery()
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            btPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                )
            )
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header + Open Keyboard CTA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "KeyConnect",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = AccentCyan
                    )
                    Text(
                        text = "إعداد الاتصال بالحاسوب (ويندوز) عبر USB أو Wi-Fi أو البلوتوث",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onOpenFullScreenKeyboard,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (connState is ConnectionUiState.Connected) AccentEmerald else AccentCyan
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("فتح الكيبورد", fontWeight = FontWeight.Bold)
                }
            }

            // Live Connection Status Banner
            ConnectionBannerCard(
                connState = connState,
                onOpenKeyboard = onOpenFullScreenKeyboard,
                onDisconnect = { orchestrator.disconnect() }
            )

            // Quick Pairing Actions (QR + Manual IP)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { showQrScanner = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("إقران فوري برمز QR")
                }

                OutlinedButton(
                    onClick = { showManualIpDialog = true },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("اتصال يدوي (IP / USB)")
                }
            }

            // Section 1: Discovered & Trusted Windows Laptops
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, Color(0xFF1F2937)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "1) الحواسيب المتاحة والمقترنة",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = AccentCyan
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "بحث تلقائي نشط",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (hosts.isEmpty()) {
                        Text(
                            text = "شغّل برنامج KeyConnect Host على اللابتوب (ويندوز). سيظهر هنا تلقائياً عبر الـ Wi-Fi أو سلك الـ USB أو البلوتوث.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        hosts.forEach { host ->
                            HostDeviceRow(
                                host = host,
                                onConnect = {
                                    orchestrator.connectToHost(host) {
                                        if (autoOpenKeyboard) {
                                            onOpenFullScreenKeyboard()
                                        }
                                    }
                                },
                                onRemoveTrust = if (host.isTrusted) {
                                    { orchestrator.store.removeTrustedHost(host.deviceId) }
                                } else null
                            )
                        }
                    }
                }
            }

            // Section 2: Transport Strategy (Auto-Failover vs Specific Medium)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, Color(0xFF1F2937)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "2) وسائط النقل والتبديل التلقائي",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = "في الوضع التلقائي الذكي، يختار التطبيق أسرع وسيط متاح (USB ثم Wi-Fi ثم البلوتوث) وينتقل بينها دون انقطاع.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val options = listOf(
                            TransportPreference.AUTO_SMART to "تلقائي ذكي (موصى به)",
                            TransportPreference.USB_ONLY to "⚡ سلك USB فقط",
                            TransportPreference.WIFI_ONLY to "📶 Wi-Fi فقط",
                            TransportPreference.BLUETOOTH_ONLY to "🔵 Bluetooth RFCOMM",
                            TransportPreference.BLUETOOTH_HID to "⌨️ Bluetooth HID بدون برنامج",
                        )
                        for ((pref, label) in options) {
                            FilterChip(
                                selected = selectedTransportPref == pref,
                                onClick = {
                                    selectedTransportPref = pref
                                    orchestrator.switchTransportPreference(pref)
                                },
                                label = { Text(label, fontSize = 12.sp) }
                            )
                        }
                    }
                }
            }

            // Section 3: Keyboard Experience Preferences
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, Color(0xFF1F2937)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "3) تفضيلات الكيبورد الكامل",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    SettingToggleRow(
                        title = "اهتزاز لمسي عند ضغط الأزرار (Haptics)",
                        subtitle = "يمنح إحساساً ملموساً يحاكي لوحة المفاتيح الفعلية",
                        checked = hapticsEnabled,
                        onCheckedChange = {
                            hapticsEnabled = it
                            store.hapticsEnabled = it
                        }
                    )

                    SettingToggleRow(
                        title = "ملء الشاشة بالوضع الأفقي (Landscape)",
                        subtitle = "يستغل كامل عرض شاشة الهاتف لتكبير الأزرار وسهولة الكتابة بكلتا اليدين",
                        checked = forceLandscape,
                        onCheckedChange = {
                            forceLandscape = it
                            store.forceLandscapeKeyboard = it
                        }
                    )

                    SettingToggleRow(
                        title = "الانتقال المباشر للكيبورد فور الاتصال",
                        subtitle = "يفتح الكيبورد تلقائياً بمجرد نجاح الاتصال بالحاسوب",
                        checked = autoOpenKeyboard,
                        onCheckedChange = {
                            autoOpenKeyboard = it
                            store.autoOpenKeyboardOnConnect = it
                        }
                    )
                }
            }
        }

        // KDE Connect-style 6-Digit PIN Verification Dialog
        if (connState is ConnectionUiState.AwaitingPairingPin) {
            val pinState = connState as ConnectionUiState.AwaitingPairingPin
            AlertDialog(
                onDismissRequest = { orchestrator.disconnect() },
                title = { Text("تأكيد الإقران الآمن (${pinState.hostName})") },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "تحقق من مطابقة رمز الأمان المكوّن من 6 أرقام على شاشة اللابتوب ثم اضغط (Accept & Trust) في ويندوز:",
                            fontSize = 13.sp
                        )
                        Surface(
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, AccentCyan)
                        ) {
                            Text(
                                text = pinState.pin,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 28.sp,
                                color = AccentCyan,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { orchestrator.disconnect() }) {
                        Text("إلغاء الطلب")
                    }
                }
            )
        }

        if (showQrScanner) {
            QrScannerDialog(
                onQrScanned = { uri ->
                    showQrScanner = false
                    orchestrator.connectViaQrUri(uri) {
                        if (autoOpenKeyboard) onOpenFullScreenKeyboard()
                    }
                },
                onDismiss = { showQrScanner = false }
            )
        }

        if (showManualIpDialog) {
            AlertDialog(
                onDismissRequest = { showManualIpDialog = false },
                title = { Text("اتصال مباشر عبر عنوان IP أو USB") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "أدخل عنوان IP الظاهر في برنامج الويندوز (أو 127.0.0.1 لنفق USB ADB، أو 192.168.42.x لتقييد USB):",
                            fontSize = 12.sp
                        )
                        OutlinedTextField(
                            value = manualIpInput,
                            onValueChange = { manualIpInput = it },
                            placeholder = { Text("مثال: 192.168.1.15 أو 127.0.0.1") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        showManualIpDialog = false
                        orchestrator.connectByManualIp(manualIpInput) {
                            if (autoOpenKeyboard) onOpenFullScreenKeyboard()
                        }
                    }) {
                        Text("اتصال الآن")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showManualIpDialog = false }) {
                        Text("إلغاء")
                    }
                }
            )
        }
    }
}

@Composable
private fun ConnectionBannerCard(
    connState: ConnectionUiState,
    onOpenKeyboard: () -> Unit,
    onDisconnect: () -> Unit,
) {
    when (connState) {
        is ConnectionUiState.Connected -> {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AccentEmerald.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, AccentEmerald),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "متصل بـ ${connState.hostName}",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        val standbyInfo = if (connState.standbyMediums.isNotEmpty()) {
                            " • احتياطي جاهز: ${connState.standbyMediums.joinToString { it.badge }}"
                        } else ""
                        Text(
                            text = "الوسيط النشط: ${connState.activeMedium.badge} (${connState.rttMs} ms)$standbyInfo",
                            fontSize = 12.sp,
                            color = AccentEmerald
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(
                            onClick = onOpenKeyboard,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald)
                        ) {
                            Text("ابدأ الكتابة")
                        }
                        IconButton(onClick = onDisconnect) {
                            Icon(Icons.Default.LinkOff, contentDescription = "قطع الاتصال", tint = Color.White)
                        }
                    }
                }
            }
        }

        is ConnectionUiState.Connecting -> {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = AccentCyan.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, AccentCyan),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text("جاري الاتصال بـ ${connState.hostName} عبر ${connState.medium.arabicLabel}...")
                }
            }
        }

        is ConnectionUiState.Error -> {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF7F1D1D).copy(alpha = 0.35f),
                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = connState.message,
                    fontSize = 13.sp,
                    color = Color(0xFFFECACA),
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        else -> Unit
    }
}

@Composable
private fun HostDeviceRow(
    host: DiscoveredHost,
    onConnect: () -> Unit,
    onRemoveTrust: (() -> Unit)?,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E293B),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onConnect() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AccentCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Computer, contentDescription = null, tint = AccentCyan)
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(host.deviceName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        if (host.isTrusted) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "جهاز موثوق",
                                tint = AccentEmerald,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (host.usbAvailable) {
                            MediumPill(Icons.Default.Usb, "USB", AccentEmerald)
                        }
                        if (host.wifiIp != null) {
                            MediumPill(Icons.Default.Wifi, host.wifiIp, AccentCyan)
                        }
                        if (host.bluetoothAddress != null) {
                            MediumPill(Icons.Default.Bluetooth, "BT", AccentIndigo)
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onRemoveTrust != null) {
                    IconButton(onClick = onRemoveTrust) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "إلغاء الإقران",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Button(onClick = onConnect, shape = RoundedCornerShape(10.dp)) {
                    Text(if (host.isTrusted) "اتصال" else "إقران")
                }
            }
        }
    }
}

@Composable
private fun MediumPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
        Text(text, fontSize = 11.sp, color = tint)
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
