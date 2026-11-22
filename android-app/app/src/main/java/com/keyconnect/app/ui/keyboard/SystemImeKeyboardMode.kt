package com.keyconnect.app.ui.keyboard

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keyconnect.app.model.KeyAction
import com.keyconnect.app.model.ModifiersState
import com.keyconnect.app.network.ConnectionOrchestrator
import com.keyconnect.app.ui.theme.AccentCyan
import com.keyconnect.app.ui.theme.AccentEmerald
import com.keyconnect.app.ui.theme.BgKeyNormal
import com.keyconnect.app.ui.theme.TextMuted

private const val SENTINEL = "\u200B\u200B"

@Composable
fun SystemImeKeyboardMode(
    orchestrator: ConnectionOrchestrator,
    hapticsEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current

    var instantCharMode by remember { mutableStateOf(true) }
    var tfValue by remember {
        mutableStateOf(TextFieldValue(text = SENTINEL, selection = TextRange(SENTINEL.length)))
    }
    var recentStreamPreview by remember { mutableStateOf("") }

    var ctrlLatched by remember { mutableStateOf(false) }
    var altLatched by remember { mutableStateOf(false) }
    var shiftLatched by remember { mutableStateOf(false) }
    var winLatched by remember { mutableStateOf(false) }

    fun haptic() {
        if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun consumeModifiers(): ModifiersState {
        val m = ModifiersState(
            ctrl = ctrlLatched,
            alt = altLatched,
            shift = shiftLatched,
            meta = winLatched
        )
        ctrlLatched = false
        altLatched = false
        shiftLatched = false
        winLatched = false
        return m
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Live Input Capture Surface (Tap anywhere to re-summon phone keyboard)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "اكتب بكيبورد هاتفك أو الإملاء الصوتي 🎤 — يظهر فوراً على اللابتوب",
                                fontSize = 12.sp,
                                color = AccentCyan,
                                fontWeight = FontWeight.SemiBold
                            )

                            FilterChip(
                                selected = !instantCharMode,
                                onClick = {
                                    instantCharMode = !instantCharMode
                                    tfValue = TextFieldValue(SENTINEL, TextRange(SENTINEL.length))
                                },
                                label = {
                                    Text(
                                        if (instantCharMode) "⚡ وضع الحرف الفوري" else "🎤 وضع الجمل والإملاء",
                                        fontSize = 11.sp
                                    )
                                }
                            )
                        }

                        Text(
                            text = recentStreamPreview.ifEmpty {
                                "اضغط هنا لإظهار كيبورد الهاتف والبدء في الكتابة..."
                            },
                            fontSize = 18.sp,
                            color = if (recentStreamPreview.isEmpty()) TextMuted else Color.White,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Invisible sentinel BasicTextField capturing every keystroke, swipe word, voice phrase & Backspace
                    BasicTextField(
                        value = tfValue,
                        onValueChange = { updated ->
                            val raw = updated.text
                            if (raw.length < SENTINEL.length) {
                                // Backspace pressed on empty buffer!
                                val backspaces = (SENTINEL.length - raw.length).coerceAtLeast(1)
                                repeat(backspaces) {
                                    orchestrator.sendKeyEvent(KeyAction.PRESS, "Backspace", consumeModifiers())
                                }
                                if (recentStreamPreview.isNotEmpty()) {
                                    recentStreamPreview = recentStreamPreview.dropLast(1)
                                }
                                tfValue = TextFieldValue(SENTINEL, TextRange(SENTINEL.length))
                            } else if (raw.length > SENTINEL.length) {
                                // In voice/swipe mode, wait until composition finishes so autocorrect doesn'tgarble
                                if (!instantCharMode && updated.composition != null) {
                                    tfValue = updated
                                } else {
                                    val delta = raw.removePrefix(SENTINEL).replace("\u200B", "")
                                    if (delta.isNotEmpty()) {
                                        val mods = consumeModifiers()
                                        if ((mods.ctrl || mods.alt || mods.meta) && delta.length == 1) {
                                            orchestrator.sendKeyEvent(KeyAction.PRESS, delta, mods)
                                        } else {
                                            orchestrator.sendUnicodeText(delta)
                                        }
                                        recentStreamPreview = (recentStreamPreview + delta).takeLast(90)
                                    }
                                    tfValue = TextFieldValue(SENTINEL, TextRange(SENTINEL.length))
                                }
                            } else {
                                tfValue = TextFieldValue(SENTINEL, TextRange(SENTINEL.length))
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            keyboardType = if (instantCharMode) KeyboardType.Password else KeyboardType.Text,
                            imeAction = ImeAction.None
                        ),
                        textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
                        cursorBrush = SolidColor(Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .alpha(0.01f)
                            .focusRequester(focusRequester)
                    )
                }
            }

            // Windows Modifier & Shortcut Control Deck right above the Phone's Soft Keyboard
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        DeckLatchKey("Ctrl", ctrlLatched, Modifier.weight(1f)) {
                            haptic(); ctrlLatched = !ctrlLatched
                        }
                        DeckLatchKey("Alt", altLatched, Modifier.weight(1f)) {
                            haptic(); altLatched = !altLatched
                        }
                        DeckLatchKey("Shift", shiftLatched, Modifier.weight(1f)) {
                            haptic(); shiftLatched = !shiftLatched
                        }
                        DeckLatchKey("⊞ Win", winLatched, Modifier.weight(1f)) {
                            haptic(); winLatched = !winLatched
                        }
                        DeckTapKey("Esc", Modifier.weight(1f)) {
                            haptic(); orchestrator.sendKeyEvent(KeyAction.PRESS, "Escape")
                        }
                        DeckTapKey("Tab", Modifier.weight(1f)) {
                            haptic(); orchestrator.sendKeyEvent(KeyAction.PRESS, "Tab", consumeModifiers())
                        }
                        DeckTapKey("Del", Modifier.weight(1f)) {
                            haptic(); orchestrator.sendKeyEvent(KeyAction.PRESS, "Delete")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        DeckTapKey("نسخ Ctrl+C", Modifier.weight(1.2f)) {
                            haptic(); orchestrator.sendShortcutCombo(listOf("ctrl"), "KeyC")
                        }
                        DeckTapKey("لصق Ctrl+V", Modifier.weight(1.2f)) {
                            haptic(); orchestrator.sendShortcutCombo(listOf("ctrl"), "KeyV")
                        }
                        DeckTapKey("تراجع Ctrl+Z", Modifier.weight(1.2f)) {
                            haptic(); orchestrator.sendShortcutCombo(listOf("ctrl"), "KeyZ")
                        }
                        DeckTapKey("◀", Modifier.weight(0.75f)) {
                            haptic(); orchestrator.sendKeyEvent(KeyAction.PRESS, "ArrowLeft")
                        }
                        DeckTapKey("▲", Modifier.weight(0.75f)) {
                            haptic(); orchestrator.sendKeyEvent(KeyAction.PRESS, "ArrowUp")
                        }
                        DeckTapKey("▼", Modifier.weight(0.75f)) {
                            haptic(); orchestrator.sendKeyEvent(KeyAction.PRESS, "ArrowDown")
                        }
                        DeckTapKey("▶", Modifier.weight(0.75f)) {
                            haptic(); orchestrator.sendKeyEvent(KeyAction.PRESS, "ArrowRight")
                        }
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun DeckLatchKey(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (active) AccentEmerald.copy(alpha = 0.35f) else BgKeyNormal,
        border = BorderStroke(1.dp, if (active) AccentEmerald else Color(0xFF334155)),
        modifier = modifier
            .height(38.dp)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (active) AccentEmerald else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun DeckTapKey(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = BgKeyNormal,
        border = BorderStroke(1.dp, Color(0xFF334155)),
        modifier = modifier
            .height(38.dp)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = label, fontSize = 11.5.sp, fontWeight = FontWeight.Medium, color = Color.White)
        }
    }
}
