package com.keyconnect.app.ui.keyboard

import com.keyconnect.app.model.KeyboardLanguageLayer

enum class KeyRole {
    CHARACTER,
    MODIFIER_LATCH,  // Ctrl, Alt, Shift, Win (supports multi-touch hold + tap latch + double-tap lock)
    ACTION_REPEAT,   // Backspace, Space, Arrows, Enter, Delete, Tab (supports hold auto-repeat)
    ACTION_SINGLE,   // Esc, F1-F12, Media, CapsLock
    LAYER_SWITCH,    // AR <-> EN, Fn toggle
    SHORTCUT_MACRO   // Copy, Paste, Undo, Alt+Tab
}

data class KeySpec(
    val id: String,
    val primaryLabel: String,
    val shiftLabel: String? = null,
    val canonicalKey: String,           // Physical PC scancode key (used for shortcuts like Ctrl+C even in Arabic!)
    val unicodeNormal: String? = null,  // Direct UTF-8 text when no Ctrl/Alt/Win is active (e.g., "ض", "لا")
    val unicodeShift: String? = null,   // Shifted UTF-8 text (e.g., "َ", "لآ", "؟")
    val weight: Float = 1f,
    val role: KeyRole = KeyRole.CHARACTER,
    val comboModifiers: List<String> = emptyList(),
)

object KeyboardLayouts {

    fun rowsForLayer(layer: KeyboardLanguageLayer): List<List<KeySpec>> = when (layer) {
        KeyboardLanguageLayer.ARABIC_101 -> arabic101Rows
        KeyboardLanguageLayer.ENGLISH_QWERTY -> englishQwertyRows
        KeyboardLanguageLayer.FN_NUMPAD -> fnNumpadRows
    }

    private val topUtilityStrip: List<KeySpec> = listOf(
        KeySpec("esc", "Esc", canonicalKey = "Escape", weight = 1.15f, role = KeyRole.ACTION_SINGLE),
        KeySpec("sc_undo", "تراجع", shiftLabel = "Ctrl+Z", canonicalKey = "KeyZ", role = KeyRole.SHORTCUT_MACRO, comboModifiers = listOf("ctrl")),
        KeySpec("sc_cut", "قص", shiftLabel = "Ctrl+X", canonicalKey = "KeyX", role = KeyRole.SHORTCUT_MACRO, comboModifiers = listOf("ctrl")),
        KeySpec("sc_copy", "نسخ", shiftLabel = "Ctrl+C", canonicalKey = "KeyC", role = KeyRole.SHORTCUT_MACRO, comboModifiers = listOf("ctrl")),
        KeySpec("sc_paste", "لصق", shiftLabel = "Ctrl+V", canonicalKey = "KeyV", role = KeyRole.SHORTCUT_MACRO, comboModifiers = listOf("ctrl")),
        KeySpec("sc_all", "الكل", shiftLabel = "Ctrl+A", canonicalKey = "KeyA", role = KeyRole.SHORTCUT_MACRO, comboModifiers = listOf("ctrl")),
        KeySpec("sc_alttab", "Alt+Tab", canonicalKey = "Tab", weight = 1.2f, role = KeyRole.SHORTCUT_MACRO, comboModifiers = listOf("alt")),
        KeySpec("home", "Home", canonicalKey = "Home", role = KeyRole.ACTION_REPEAT),
        KeySpec("end", "End", canonicalKey = "End", role = KeyRole.ACTION_REPEAT),
        KeySpec("pgup", "PgUp", canonicalKey = "PageUp", role = KeyRole.ACTION_REPEAT),
        KeySpec("pgdn", "PgDn", canonicalKey = "PageDown", role = KeyRole.ACTION_REPEAT),
        KeySpec("del", "Del", canonicalKey = "Delete", weight = 1.15f, role = KeyRole.ACTION_REPEAT),
    )

    private val numberRowArabic: List<KeySpec> = listOf(
        KeySpec("ar_grave", "ذ", "ّ", "Backquote", "ذ", "ّ"),
        KeySpec("n1", "1", "!", "Digit1", "1", "!"),
        KeySpec("n2", "2", "@", "Digit2", "2", "@"),
        KeySpec("n3", "3", "#", "Digit3", "3", "#"),
        KeySpec("n4", "4", "$", "Digit4", "4", "$"),
        KeySpec("n5", "5", "%", "Digit5", "5", "%"),
        KeySpec("n6", "6", "^", "Digit6", "6", "^"),
        KeySpec("n7", "7", "&", "Digit7", "7", "&"),
        KeySpec("n8", "8", "*", "Digit8", "8", "*"),
        KeySpec("n9", "9", ")", "Digit9", "9", ")"),
        KeySpec("n0", "0", "(", "Digit0", "0", "("),
        KeySpec("minus", "-", "_", "Minus", "-", "_"),
        KeySpec("equal", "=", "+", "Equal", "=", "+"),
        KeySpec("bksp", "⌫", canonicalKey = "Backspace", weight = 1.75f, role = KeyRole.ACTION_REPEAT),
    )

    private val arabic101Rows: List<List<KeySpec>> = listOf(
        topUtilityStrip,
        numberRowArabic,
        listOf(
            KeySpec("tab", "Tab ⇥", canonicalKey = "Tab", weight = 1.35f, role = KeyRole.ACTION_REPEAT),
            KeySpec("ar_q", "ض", "َ", "KeyQ", "ض", "َ"),
            KeySpec("ar_w", "ص", "ً", "KeyW", "ص", "ً"),
            KeySpec("ar_e", "ث", "ُ", "KeyE", "ث", "ُ"),
            KeySpec("ar_r", "ق", "ٌ", "KeyR", "ق", "ٌ"),
            KeySpec("ar_t", "ف", "لإ", "KeyT", "ف", "لإ"),
            KeySpec("ar_y", "غ", "إ", "KeyY", "غ", "إ"),
            KeySpec("ar_u", "ع", "‘", "KeyU", "ع", "‘"),
            KeySpec("ar_i", "ه", "÷", "KeyI", "ه", "÷"),
            KeySpec("ar_o", "خ", "×", "KeyO", "خ", "×"),
            KeySpec("ar_p", "ح", "؛", "KeyP", "ح", "؛"),
            KeySpec("ar_lb", "ج", "<", "BracketLeft", "ج", "<"),
            KeySpec("ar_rb", "د", ">", "BracketRight", "د", ">"),
            KeySpec("ar_bs", "\\", "|", "Backslash", "\\", "|", weight = 1.1f),
        ),
        listOf(
            KeySpec("caps", "Caps", canonicalKey = "CapsLock", weight = 1.55f, role = KeyRole.ACTION_SINGLE),
            KeySpec("ar_a", "ش", "ِ", "KeyA", "ش", "ِ"),
            KeySpec("ar_s", "س", "ٍ", "KeyS", "س", "ٍ"),
            KeySpec("ar_d", "ي", "]", "KeyD", "ي", "]"),
            KeySpec("ar_f", "ب", "[", "KeyF", "ب", "["),
            KeySpec("ar_g", "ل", "لأ", "KeyG", "ل", "لأ"),
            KeySpec("ar_h", "ا", "أ", "KeyH", "ا", "أ"),
            KeySpec("ar_j", "ت", "ـ", "KeyJ", "ت", "ـ"),
            KeySpec("ar_k", "ن", "،", "KeyK", "ن", "،"),
            KeySpec("ar_l", "م", "/", "KeyL", "م", "/"),
            KeySpec("ar_semi", "ك", ":", "Semicolon", "ك", ":"),
            KeySpec("ar_quote", "ط", "\"", "Quote", "ط", "\""),
            KeySpec("enter", "↵ Enter", canonicalKey = "Enter", weight = 1.85f, role = KeyRole.ACTION_REPEAT),
        ),
        listOf(
            KeySpec("shift_l", "⇧ Shift", canonicalKey = "ShiftLeft", weight = 1.75f, role = KeyRole.MODIFIER_LATCH),
            KeySpec("ar_z", "ئ", "~", "KeyZ", "ئ", "~"),
            KeySpec("ar_x", "ء", "ْ", "KeyX", "ء", "ْ"),
            KeySpec("ar_c", "ؤ", "}", "KeyC", "ؤ", "}"),
            KeySpec("ar_v", "ر", "{", "KeyV", "ر", "{"),
            KeySpec("ar_b", "لا", "لآ", "KeyB", "لا", "لآ"),
            KeySpec("ar_n", "ى", "آ", "KeyN", "ى", "آ"),
            KeySpec("ar_m", "ة", "’", "KeyM", "ة", "’"),
            KeySpec("ar_comma", "و", ",", "Comma", "و", ","),
            KeySpec("ar_dot", "ز", ".", "Period", "ز", "."),
            KeySpec("ar_slash", "ظ", "؟", "Slash", "ظ", "؟"),
            KeySpec("arr_up", "▲", canonicalKey = "ArrowUp", weight = 1.15f, role = KeyRole.ACTION_REPEAT),
            KeySpec("shift_r", "⇧", canonicalKey = "ShiftRight", weight = 1.25f, role = KeyRole.MODIFIER_LATCH),
        ),
        bottomControlRow(nextLangLabel = "🌐 EN")
    )

    private val englishQwertyRows: List<List<KeySpec>> = listOf(
        topUtilityStrip,
        listOf(
            KeySpec("en_grave", "`", "~", "Backquote", "`", "~"),
            KeySpec("en_1", "1", "!", "Digit1", "1", "!"),
            KeySpec("en_2", "2", "@", "Digit2", "2", "@"),
            KeySpec("en_3", "3", "#", "Digit3", "3", "#"),
            KeySpec("en_4", "4", "$", "Digit4", "4", "$"),
            KeySpec("en_5", "5", "%", "Digit5", "5", "%"),
            KeySpec("en_6", "6", "^", "Digit6", "6", "^"),
            KeySpec("en_7", "7", "&", "Digit7", "7", "&"),
            KeySpec("en_8", "8", "*", "Digit8", "8", "*"),
            KeySpec("en_9", "9", "(", "Digit9", "9", "("),
            KeySpec("en_0", "0", ")", "Digit0", "0", ")"),
            KeySpec("en_min", "-", "_", "Minus", "-", "_"),
            KeySpec("en_eq", "=", "+", "Equal", "=", "+"),
            KeySpec("bksp", "⌫", canonicalKey = "Backspace", weight = 1.75f, role = KeyRole.ACTION_REPEAT),
        ),
        listOf(
            KeySpec("tab", "Tab ⇥", canonicalKey = "Tab", weight = 1.35f, role = KeyRole.ACTION_REPEAT),
            KeySpec("q", "q", "Q", "KeyQ"),
            KeySpec("w", "w", "W", "KeyW"),
            KeySpec("e", "e", "E", "KeyE"),
            KeySpec("r", "r", "R", "KeyR"),
            KeySpec("t", "t", "T", "KeyT"),
            KeySpec("y", "y", "Y", "KeyY"),
            KeySpec("u", "u", "U", "KeyU"),
            KeySpec("i", "i", "I", "KeyI"),
            KeySpec("o", "o", "O", "KeyO"),
            KeySpec("p", "p", "P", "KeyP"),
            KeySpec("lb", "[", "{", "BracketLeft", "[", "{"),
            KeySpec("rb", "]", "}", "BracketRight", "]", "}"),
            KeySpec("bs", "\\", "|", "Backslash", "\\", "|", weight = 1.1f),
        ),
        listOf(
            KeySpec("caps", "Caps", canonicalKey = "CapsLock", weight = 1.55f, role = KeyRole.ACTION_SINGLE),
            KeySpec("a", "a", "A", "KeyA"),
            KeySpec("s", "s", "S", "KeyS"),
            KeySpec("d", "d", "D", "KeyD"),
            KeySpec("f", "f", "F", "KeyF"),
            KeySpec("g", "g", "G", "KeyG"),
            KeySpec("h", "h", "H", "KeyH"),
            KeySpec("j", "j", "J", "KeyJ"),
            KeySpec("k", "k", "K", "KeyK"),
            KeySpec("l", "l", "L", "KeyL"),
            KeySpec("semi", ";", ":", "Semicolon", ";", ":"),
            KeySpec("quote", "'", "\"", "Quote", "'", "\""),
            KeySpec("enter", "↵ Enter", canonicalKey = "Enter", weight = 1.85f, role = KeyRole.ACTION_REPEAT),
        ),
        listOf(
            KeySpec("shift_l", "⇧ Shift", canonicalKey = "ShiftLeft", weight = 1.75f, role = KeyRole.MODIFIER_LATCH),
            KeySpec("z", "z", "Z", "KeyZ"),
            KeySpec("x", "x", "X", "KeyX"),
            KeySpec("c", "c", "C", "KeyC"),
            KeySpec("v", "v", "V", "KeyV"),
            KeySpec("b", "b", "B", "KeyB"),
            KeySpec("n", "n", "N", "KeyN"),
            KeySpec("m", "m", "M", "KeyM"),
            KeySpec("comma", ",", "<", "Comma", ",", "<"),
            KeySpec("dot", ".", ">", "Period", ".", ">"),
            KeySpec("slash", "/", "?", "Slash", "/", "?"),
            KeySpec("arr_up", "▲", canonicalKey = "ArrowUp", weight = 1.15f, role = KeyRole.ACTION_REPEAT),
            KeySpec("shift_r", "⇧", canonicalKey = "ShiftRight", weight = 1.25f, role = KeyRole.MODIFIER_LATCH),
        ),
        bottomControlRow(nextLangLabel = "🌐 عربي")
    )

    private val fnNumpadRows: List<List<KeySpec>> = listOf(
        listOf(
            KeySpec("esc", "Esc", canonicalKey = "Escape", role = KeyRole.ACTION_SINGLE),
            KeySpec("f1", "F1", canonicalKey = "F1", role = KeyRole.ACTION_SINGLE),
            KeySpec("f2", "F2", canonicalKey = "F2", role = KeyRole.ACTION_SINGLE),
            KeySpec("f3", "F3", canonicalKey = "F3", role = KeyRole.ACTION_SINGLE),
            KeySpec("f4", "F4", canonicalKey = "F4", role = KeyRole.ACTION_SINGLE),
            KeySpec("f5", "F5", canonicalKey = "F5", role = KeyRole.ACTION_SINGLE),
            KeySpec("f6", "F6", canonicalKey = "F6", role = KeyRole.ACTION_SINGLE),
            KeySpec("f7", "F7", canonicalKey = "F7", role = KeyRole.ACTION_SINGLE),
            KeySpec("f8", "F8", canonicalKey = "F8", role = KeyRole.ACTION_SINGLE),
            KeySpec("f9", "F9", canonicalKey = "F9", role = KeyRole.ACTION_SINGLE),
            KeySpec("f10", "F10", canonicalKey = "F10", role = KeyRole.ACTION_SINGLE),
            KeySpec("f11", "F11", canonicalKey = "F11", role = KeyRole.ACTION_SINGLE),
            KeySpec("f12", "F12", canonicalKey = "F12", role = KeyRole.ACTION_SINGLE),
        ),
        listOf(
            KeySpec("prtsc", "PrtSc", canonicalKey = "PrintScreen", role = KeyRole.ACTION_SINGLE),
            KeySpec("ins", "Insert", canonicalKey = "Insert", role = KeyRole.ACTION_SINGLE),
            KeySpec("del2", "Delete", canonicalKey = "Delete", role = KeyRole.ACTION_REPEAT),
            KeySpec("home2", "Home", canonicalKey = "Home", role = KeyRole.ACTION_REPEAT),
            KeySpec("end2", "End", canonicalKey = "End", role = KeyRole.ACTION_REPEAT),
            KeySpec("np7", "7", canonicalKey = "Digit7", unicodeNormal = "7"),
            KeySpec("np8", "8", canonicalKey = "Digit8", unicodeNormal = "8"),
            KeySpec("np9", "9", canonicalKey = "Digit9", unicodeNormal = "9"),
            KeySpec("np_div", "/", canonicalKey = "Slash", unicodeNormal = "/"),
            KeySpec("bksp", "⌫", canonicalKey = "Backspace", weight = 1.4f, role = KeyRole.ACTION_REPEAT),
        ),
        listOf(
            KeySpec("vmute", "🔇 كتم", canonicalKey = "VolumeMute", weight = 1.2f, role = KeyRole.ACTION_SINGLE),
            KeySpec("vdown", "🔉 خفض", canonicalKey = "VolumeDown", weight = 1.2f, role = KeyRole.ACTION_REPEAT),
            KeySpec("vup", "🔊 رفع", canonicalKey = "VolumeUp", weight = 1.2f, role = KeyRole.ACTION_REPEAT),
            KeySpec("pgup2", "PgUp", canonicalKey = "PageUp", role = KeyRole.ACTION_REPEAT),
            KeySpec("np4", "4", canonicalKey = "Digit4", unicodeNormal = "4"),
            KeySpec("np5", "5", canonicalKey = "Digit5", unicodeNormal = "5"),
            KeySpec("np6", "6", canonicalKey = "Digit6", unicodeNormal = "6"),
            KeySpec("np_mul", "*", canonicalKey = "Digit8", unicodeNormal = "*"),
        ),
        listOf(
            KeySpec("mprev", "⏮ السابق", canonicalKey = "MediaPrevTrack", weight = 1.2f, role = KeyRole.ACTION_SINGLE),
            KeySpec("mplay", "⏯ تشغيل", canonicalKey = "MediaPlayPause", weight = 1.2f, role = KeyRole.ACTION_SINGLE),
            KeySpec("mnext", "⏭ التالي", canonicalKey = "MediaNextTrack", weight = 1.2f, role = KeyRole.ACTION_SINGLE),
            KeySpec("pgdn2", "PgDn", canonicalKey = "PageDown", role = KeyRole.ACTION_REPEAT),
            KeySpec("np1", "1", canonicalKey = "Digit1", unicodeNormal = "1"),
            KeySpec("np2", "2", canonicalKey = "Digit2", unicodeNormal = "2"),
            KeySpec("np3", "3", canonicalKey = "Digit3", unicodeNormal = "3"),
            KeySpec("np_sub", "-", canonicalKey = "Minus", unicodeNormal = "-"),
        ),
        listOf(
            KeySpec("win_d", "Win+D سطح المكتب", canonicalKey = "KeyD", weight = 1.8f, role = KeyRole.SHORTCUT_MACRO, comboModifiers = listOf("meta")),
            KeySpec("taskmgr", "Ctrl+Shift+Esc", canonicalKey = "Escape", weight = 1.8f, role = KeyRole.SHORTCUT_MACRO, comboModifiers = listOf("ctrl", "shift")),
            KeySpec("alt_f4", "Alt+F4", canonicalKey = "F4", weight = 1.3f, role = KeyRole.SHORTCUT_MACRO, comboModifiers = listOf("alt")),
            KeySpec("np0", "0", canonicalKey = "Digit0", unicodeNormal = "0", weight = 1.5f),
            KeySpec("np_dot", ".", canonicalKey = "Period", unicodeNormal = "."),
            KeySpec("np_add", "+", canonicalKey = "Equal", unicodeNormal = "+"),
            KeySpec("arr_up", "▲", canonicalKey = "ArrowUp", role = KeyRole.ACTION_REPEAT),
        ),
        bottomControlRow(nextLangLabel = "🌐 رجوع")
    )

    private fun bottomControlRow(nextLangLabel: String): List<KeySpec> = listOf(
        KeySpec("ctrl_l", "Ctrl", canonicalKey = "CtrlLeft", weight = 1.3f, role = KeyRole.MODIFIER_LATCH),
        KeySpec("fn_toggle", "Fn", canonicalKey = "Fn", weight = 1.05f, role = KeyRole.LAYER_SWITCH),
        KeySpec("win_l", "⊞ Win", canonicalKey = "MetaLeft", weight = 1.2f, role = KeyRole.MODIFIER_LATCH),
        KeySpec("alt_l", "Alt", canonicalKey = "AltLeft", weight = 1.2f, role = KeyRole.MODIFIER_LATCH),
        KeySpec("lang_switch", nextLangLabel, canonicalKey = "LangSwitch", weight = 1.35f, role = KeyRole.LAYER_SWITCH),
        KeySpec("space", "مسافة  •  اسحب لتحريك المؤشر", canonicalKey = "Space", weight = 4.6f, role = KeyRole.ACTION_REPEAT),
        KeySpec("arr_left", "◀", canonicalKey = "ArrowLeft", weight = 1.15f, role = KeyRole.ACTION_REPEAT),
        KeySpec("arr_down", "▼", canonicalKey = "ArrowDown", weight = 1.15f, role = KeyRole.ACTION_REPEAT),
        KeySpec("arr_right", "▶", canonicalKey = "ArrowRight", weight = 1.15f, role = KeyRole.ACTION_REPEAT),
    )
}
