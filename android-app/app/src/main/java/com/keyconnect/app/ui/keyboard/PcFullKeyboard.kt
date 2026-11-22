package com.keyconnect.app.ui.keyboard

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keyconnect.app.model.KeyAction
import com.keyconnect.app.model.KeyboardLanguageLayer
import com.keyconnect.app.model.ModifiersState
import com.keyconnect.app.network.ConnectionOrchestrator
import com.keyconnect.app.ui.theme.AccentCyan
import com.keyconnect.app.ui.theme.AccentEmerald
import com.keyconnect.app.ui.theme.BgKeyNormal
import com.keyconnect.app.ui.theme.BgKeySpecial
import com.keyconnect.app.ui.theme.TextMuted
import com.keyconnect.app.ui.theme.TextPrimary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

private enum class LatchMode { OFF, ONE_SHOT, LOCKED }

@Composable
fun PcFullKeyboard(
    orchestrator: ConnectionOrchestrator,
    currentLayer: KeyboardLanguageLayer,
    hapticsEnabled: Boolean,
    onLayerChange: (KeyboardLanguageLayer) -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val swipeStepPx = with(density) { 22.dp.toPx() }

    // Latch state for single-handed shortcuts + physical hold map for two-thumb multi-touch chords
    val latchedMods = remember { mutableStateMapOf<String, LatchMode>() }
    val physicallyHeldMods = remember { mutableStateMapOf<String, Boolean>() }
    var capsLocked by remember { mutableStateOf(false) }
    var previousTextLayer by remember {
        mutableStateOf(
            if (currentLayer == KeyboardLanguageLayer.FN_NUMPAD) KeyboardLanguageLayer.ARABIC_101
            else currentLayer
        )
    }

    fun triggerHaptic(isRelease: Boolean = false) {
        if (!hapticsEnabled) return
        val constant = if (isRelease) {
            HapticFeedbackConstants.CLOCK_TICK
        } else {
            HapticFeedbackConstants.KEYBOARD_TAP
        }
        view.performHapticFeedback(constant)
    }

    fun isModActive(modGroup: String): Boolean {
        return when (modGroup) {
            "ctrl" -> physicallyHeldMods["CtrlLeft"] == true ||
                (latchedMods["CtrlLeft"] ?: LatchMode.OFF) != LatchMode.OFF
            "alt" -> physicallyHeldMods["AltLeft"] == true ||
                (latchedMods["AltLeft"] ?: LatchMode.OFF) != LatchMode.OFF
            "meta" -> physicallyHeldMods["MetaLeft"] == true ||
                (latchedMods["MetaLeft"] ?: LatchMode.OFF) != LatchMode.OFF
            "shift" -> physicallyHeldMods["ShiftLeft"] == true ||
                physicallyHeldMods["ShiftRight"] == true ||
                (latchedMods["ShiftLeft"] ?: LatchMode.OFF) != LatchMode.OFF ||
                (latchedMods["ShiftRight"] ?: LatchMode.OFF) != LatchMode.OFF
            else -> false
        }
    }

    fun currentModifiersState(): ModifiersState = ModifiersState(
        ctrl = isModActive("ctrl"),
        alt = isModActive("alt"),
        shift = isModActive("shift"),
        meta = isModActive("meta"),
    )

    fun clearOneShotLatches() {
        for (k in listOf("CtrlLeft", "AltLeft", "ShiftLeft", "ShiftRight", "MetaLeft")) {
            if (latchedMods[k] == LatchMode.ONE_SHOT) {
                latchedMods[k] = LatchMode.OFF
            }
        }
    }

    fun executeKeySpec(spec: KeySpec) {
        val mods = currentModifiersState()
        val hasShortcutMod = mods.ctrl || mods.alt || mods.meta
        val shiftEffective = mods.shift xor capsLocked

        when (spec.role) {
            KeyRole.LAYER_SWITCH -> {
                if (spec.id == "fn_toggle") {
                    if (currentLayer == KeyboardLanguageLayer.FN_NUMPAD) {
                        onLayerChange(previousTextLayer)
                    } else {
                        previousTextLayer = currentLayer
                        onLayerChange(KeyboardLanguageLayer.FN_NUMPAD)
                    }
                } else {
                    val next = when (currentLayer) {
                        KeyboardLanguageLayer.ARABIC_101 -> KeyboardLanguageLayer.ENGLISH_QWERTY
                        KeyboardLanguageLayer.ENGLISH_QWERTY -> KeyboardLanguageLayer.ARABIC_101
                        KeyboardLanguageLayer.FN_NUMPAD -> previousTextLayer
                    }
                    previousTextLayer = next
                    onLayerChange(next)
                }
            }

            KeyRole.SHORTCUT_MACRO -> {
                orchestrator.sendShortcutCombo(spec.comboModifiers, spec.canonicalKey)
                clearOneShotLatches()
            }

            KeyRole.ACTION_SINGLE -> {
                if (spec.canonicalKey == "CapsLock") {
                    capsLocked = !capsLocked
                } else {
                    orchestrator.sendKeyEvent(KeyAction.PRESS, spec.canonicalKey, mods)
                    clearOneShotLatches()
                }
            }

            KeyRole.ACTION_REPEAT -> {
                orchestrator.sendKeyEvent(KeyAction.PRESS, spec.canonicalKey, mods)
                clearOneShotLatches()
            }

            KeyRole.CHARACTER -> {
                // If Ctrl / Alt / Win is held (e.g., Ctrl+C while on Arabic layout), send physical scancode!
                if (hasShortcutMod) {
                    orchestrator.sendKeyEvent(KeyAction.PRESS, spec.canonicalKey, mods)
                } else if (spec.unicodeNormal != null) {
                    val textToEmit = if (mods.shift && spec.unicodeShift != null) {
                        spec.unicodeShift
                    } else {
                        spec.unicodeNormal
                    }
                    orchestrator.sendUnicodeText(textToEmit)
                } else {
                    // English letter: respect Shift / CapsLock via text or VK
                    if (spec.primaryLabel.length == 1 && spec.primaryLabel[0].isLetter()) {
                        val ch = if (shiftEffective) spec.primaryLabel.uppercase() else spec.primaryLabel.lowercase()
                        orchestrator.sendUnicodeText(ch)
                    } else {
                        orchestrator.sendKeyEvent(KeyAction.PRESS, spec.canonicalKey, mods)
                    }
                }
                clearOneShotLatches()
            }

            KeyRole.MODIFIER_LATCH -> Unit
        }
    }

    val rows = KeyboardLayouts.rowsForLayer(currentLayer)
    val shiftShown = isModActive("shift") xor capsLocked

    // Physical PC keyboards always position Esc/Tab/Shift/Ctrl on the left and Backspace/Enter/Arrows on the right
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 3.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            rows.forEachIndexed { rowIndex, rowSpecs ->
                val rowWeight = if (rowIndex == 0) 0.78f else 1.04f
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(rowWeight),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    rowSpecs.forEach { spec ->
                        val latchState = latchedMods[spec.canonicalKey] ?: LatchMode.OFF
                        val isHeldMod = physicallyHeldMods[spec.canonicalKey] == true
                        val isCapsActive = spec.canonicalKey == "CapsLock" && capsLocked
                        val isFnActive = spec.id == "fn_toggle" && currentLayer == KeyboardLanguageLayer.FN_NUMPAD

                        KeyCapButton(
                            spec = spec,
                            shiftShown = shiftShown,
                            isHighlighted = isHeldMod || latchState != LatchMode.OFF || isCapsActive || isFnActive,
                            isLocked = latchState == LatchMode.LOCKED || isCapsActive,
                            swipeStepPx = swipeStepPx,
                            modifier = Modifier
                                .weight(spec.weight)
                                .fillMaxHeight(),
                            onModifierDown = {
                                triggerHaptic(false)
                                physicallyHeldMods[spec.canonicalKey] = true
                                orchestrator.sendKeyEvent(KeyAction.DOWN, spec.canonicalKey)
                            },
                            onModifierUp = { pressDurationMs, wasTappedQuickly ->
                                physicallyHeldMods[spec.canonicalKey] = false
                                orchestrator.sendKeyEvent(KeyAction.UP, spec.canonicalKey)
                                if (wasTappedQuickly && pressDurationMs < 280L) {
                                    // Cycle OFF -> ONE_SHOT -> LOCKED -> OFF for effortless one-handed shortcuts
                                    val next = when (latchedMods[spec.canonicalKey] ?: LatchMode.OFF) {
                                        LatchMode.OFF -> LatchMode.ONE_SHOT
                                        LatchMode.ONE_SHOT -> LatchMode.LOCKED
                                        LatchMode.LOCKED -> LatchMode.OFF
                                    }
                                    latchedMods[spec.canonicalKey] = next
                                }
                            },
                            onNormalPress = {
                                triggerHaptic(false)
                                executeKeySpec(spec)
                            },
                            onSpaceSwipeCursor = { directionRight ->
                                triggerHaptic(true)
                                orchestrator.sendKeyEvent(
                                    KeyAction.PRESS,
                                    if (directionRight) "ArrowRight" else "ArrowLeft",
                                    currentModifiersState()
                                )
                            },
                            scope = scope
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyCapButton(
    spec: KeySpec,
    shiftShown: Boolean,
    isHighlighted: Boolean,
    isLocked: Boolean,
    swipeStepPx: Float,
    modifier: Modifier = Modifier,
    onModifierDown: () -> Unit,
    onModifierUp: (durationMs: Long, quickTap: Boolean) -> Unit,
    onNormalPress: () -> Unit,
    onSpaceSwipeCursor: (directionRight: Boolean) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    var pressedVisual by remember { mutableStateOf(false) }

    val baseBg = when {
        pressedVisual -> AccentCyan.copy(alpha = 0.85f)
        isLocked -> AccentEmerald.copy(alpha = 0.35f)
        isHighlighted -> AccentCyan.copy(alpha = 0.28f)
        spec.role == KeyRole.CHARACTER -> BgKeyNormal
        else -> BgKeySpecial
    }

    val borderColor = when {
        pressedVisual -> AccentCyan
        isLocked -> AccentEmerald
        isHighlighted -> AccentCyan
        else -> Color(0xFF283548)
    }

    val textColor = if (pressedVisual) Color(0xFF04131F) else TextPrimary

    Surface(
        shape = RoundedCornerShape(7.dp),
        color = baseBg,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.pointerInput(spec.id) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val downTime = System.currentTimeMillis()
                pressedVisual = true

                if (spec.role == KeyRole.MODIFIER_LATCH) {
                    onModifierDown()
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                    pressedVisual = false
                    val elapsed = System.currentTimeMillis() - downTime
                    onModifierUp(elapsed, elapsed < 280L)
                } else if (spec.id == "space") {
                    // Spacebar supports horizontal swipe-to-move-cursor + tap/hold repeat!
                    var accumDx = 0f
                    var swipedCursor = false
                    var lastX = down.position.x
                    var repeatJob: Job? = scope.launch {
                        delay(420L)
                        while (isActive && !swipedCursor) {
                            onNormalPress()
                            delay(55L)
                        }
                    }

                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull()
                        if (change != null && change.pressed) {
                            val dx = change.position.x - lastX
                            lastX = change.position.x
                            accumDx += dx
                            if (abs(accumDx) >= swipeStepPx) {
                                swipedCursor = true
                                repeatJob?.cancel()
                                repeatJob = null
                                val steps = (accumDx / swipeStepPx).toInt()
                                accumDx -= steps * swipeStepPx
                                repeat(abs(steps)) {
                                    onSpaceSwipeCursor(steps > 0)
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    repeatJob?.cancel()
                    pressedVisual = false
                    if (!swipedCursor && (System.currentTimeMillis() - downTime) < 420L) {
                        onNormalPress()
                    }
                } else {
                    onNormalPress()
                    var repeatJob: Job? = null
                    if (spec.role == KeyRole.ACTION_REPEAT || spec.role == KeyRole.CHARACTER) {
                        repeatJob = scope.launch {
                            delay(340L)
                            while (isActive) {
                                onNormalPress()
                                delay(45L)
                            }
                        }
                    }

                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })

                    repeatJob?.cancel()
                    pressedVisual = false
                }
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            // Secondary shifted legend in top-end corner
            val subLegend = spec.shiftLabel
            if (subLegend != null && spec.primaryLabel.lowercase() != subLegend.lowercase()) {
                Text(
                    text = subLegend,
                    fontSize = 9.sp,
                    color = if (pressedVisual) Color(0xFF04131F) else TextMuted,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }

            val centerText = when {
                shiftShown && spec.shiftLabel != null && spec.role == KeyRole.CHARACTER -> spec.shiftLabel
                else -> spec.primaryLabel
            }

            Text(
                text = centerText,
                fontSize = when {
                    centerText.length == 1 -> 16.sp
                    centerText.length <= 3 -> 13.sp
                    else -> 11.sp
                },
                fontWeight = if (spec.role == KeyRole.CHARACTER) FontWeight.SemiBold else FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
