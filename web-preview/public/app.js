const ARABIC_101_ROWS = [
  [
    { id: 'esc', label: 'Esc', key: 'Escape', special: true, w: 1.1 },
    { id: 'undo', label: 'تراجع', sub: 'Ctrl+Z', combo: 'ctrl+KeyZ', special: true },
    { id: 'cut', label: 'قص', sub: 'Ctrl+X', combo: 'ctrl+KeyX', special: true },
    { id: 'copy', label: 'نسخ', sub: 'Ctrl+C', combo: 'ctrl+KeyC', special: true },
    { id: 'paste', label: 'لصق', sub: 'Ctrl+V', combo: 'ctrl+KeyV', special: true },
    { id: 'selall', label: 'الكل', sub: 'Ctrl+A', combo: 'ctrl+KeyA', special: true },
    { id: 'home', label: 'Home', key: 'Home', special: true },
    { id: 'end', label: 'End', key: 'End', special: true },
    { id: 'del', label: 'Del', key: 'Delete', special: true, w: 1.15 },
  ],
  [
    { label: 'ذ', shift: 'ّ', key: 'Backquote' },
    { label: '1', shift: '!', key: 'Digit1' },
    { label: '2', shift: '@', key: 'Digit2' },
    { label: '3', shift: '#', key: 'Digit3' },
    { label: '4', shift: '$', key: 'Digit4' },
    { label: '5', shift: '%', key: 'Digit5' },
    { label: '6', shift: '^', key: 'Digit6' },
    { label: '7', shift: '&', key: 'Digit7' },
    { label: '8', shift: '*', key: 'Digit8' },
    { label: '9', shift: ')', key: 'Digit9' },
    { label: '0', shift: '(', key: 'Digit0' },
    { label: '-', shift: '_', key: 'Minus' },
    { label: '=', shift: '+', key: 'Equal' },
    { label: '⌫', key: 'Backspace', special: true, w: 1.75 },
  ],
  [
    { label: 'Tab ⇥', key: 'Tab', special: true, w: 1.35 },
    { label: 'ض', shift: 'َ', key: 'KeyQ' },
    { label: 'ص', shift: 'ً', key: 'KeyW' },
    { label: 'ث', shift: 'ُ', key: 'KeyE' },
    { label: 'ق', shift: 'ٌ', key: 'KeyR' },
    { label: 'ف', shift: 'لإ', key: 'KeyT' },
    { label: 'غ', shift: 'إ', key: 'KeyY' },
    { label: 'ع', shift: '‘', key: 'KeyU' },
    { label: 'ه', shift: '÷', key: 'KeyI' },
    { label: 'خ', shift: '×', key: 'KeyO' },
    { label: 'ح', shift: '؛', key: 'KeyP' },
    { label: 'ج', shift: '<', key: 'BracketLeft' },
    { label: 'د', shift: '>', key: 'BracketRight' },
    { label: '\\', shift: '|', key: 'Backslash', w: 1.1 },
  ],
  [
    { label: 'Caps', key: 'CapsLock', special: true, w: 1.5 },
    { label: 'ش', shift: 'ِ', key: 'KeyA' },
    { label: 'س', shift: 'ٍ', key: 'KeyS' },
    { label: 'ي', shift: ']', key: 'KeyD' },
    { label: 'ب', shift: '[', key: 'KeyF' },
    { label: 'ل', shift: 'لأ', key: 'KeyG' },
    { label: 'ا', shift: 'أ', key: 'KeyH' },
    { label: 'ت', shift: 'ـ', key: 'KeyJ' },
    { label: 'ن', shift: '،', key: 'KeyK' },
    { label: 'م', shift: '/', key: 'KeyL' },
    { label: 'ك', shift: ':', key: 'Semicolon' },
    { label: 'ط', shift: '"', key: 'Quote' },
    { label: '↵ Enter', key: 'Enter', special: true, w: 1.85 },
  ],
  [
    { label: '⇧ Shift', mod: 'shift', special: true, w: 1.75 },
    { label: 'ئ', shift: '~', key: 'KeyZ' },
    { label: 'ء', shift: 'ْ', key: 'KeyX' },
    { label: 'ؤ', shift: '}', key: 'KeyC' },
    { label: 'ر', shift: '{', key: 'KeyV' },
    { label: 'لا', shift: 'لآ', key: 'KeyB' },
    { label: 'ى', shift: 'آ', key: 'KeyN' },
    { label: 'ة', shift: '’', key: 'KeyM' },
    { label: 'و', shift: ',', key: 'Comma' },
    { label: 'ز', shift: '.', key: 'Period' },
    { label: 'ظ', shift: '؟', key: 'Slash' },
    { label: '▲', key: 'ArrowUp', special: true, w: 1.1 },
    { label: '⇧', mod: 'shift', special: true, w: 1.2 },
  ],
  [
    { label: 'Ctrl', mod: 'ctrl', special: true, w: 1.25 },
    { label: 'Fn', layer: 'fn', special: true, w: 1.05 },
    { label: '⊞ Win', mod: 'meta', special: true, w: 1.15 },
    { label: 'Alt', mod: 'alt', special: true, w: 1.15 },
    { label: '🌐 EN', layer: 'en', special: true, w: 1.3 },
    { id: 'space', label: 'مسافة  •  اسحب لتحريك المؤشر', key: 'Space', w: 4.5 },
    { label: '◀', key: 'ArrowLeft', special: true, w: 1.1 },
    { label: '▼', key: 'ArrowDown', special: true, w: 1.1 },
    { label: '▶', key: 'ArrowRight', special: true, w: 1.1 },
  ],
];

const ENGLISH_QWERTY_ROWS = [
  ARABIC_101_ROWS[0],
  [
    { label: '`', shift: '~', key: 'Backquote' },
    { label: '1', shift: '!', key: 'Digit1' },
    { label: '2', shift: '@', key: 'Digit2' },
    { label: '3', shift: '#', key: 'Digit3' },
    { label: '4', shift: '$', key: 'Digit4' },
    { label: '5', shift: '%', key: 'Digit5' },
    { label: '6', shift: '^', key: 'Digit6' },
    { label: '7', shift: '&', key: 'Digit7' },
    { label: '8', shift: '*', key: 'Digit8' },
    { label: '9', shift: '(', key: 'Digit9' },
    { label: '0', shift: ')', key: 'Digit0' },
    { label: '-', shift: '_', key: 'Minus' },
    { label: '=', shift: '+', key: 'Equal' },
    { label: '⌫', key: 'Backspace', special: true, w: 1.75 },
  ],
  [
    { label: 'Tab ⇥', key: 'Tab', special: true, w: 1.35 },
    ...'qwertyuiop'.split('').map((c) => ({ label: c, shift: c.toUpperCase(), key: 'Key' + c.toUpperCase() })),
    { label: '[', shift: '{', key: 'BracketLeft' },
    { label: ']', shift: '}', key: 'BracketRight' },
    { label: '\\', shift: '|', key: 'Backslash', w: 1.1 },
  ],
  [
    { label: 'Caps', key: 'CapsLock', special: true, w: 1.5 },
    ...'asdfghjkl'.split('').map((c) => ({ label: c, shift: c.toUpperCase(), key: 'Key' + c.toUpperCase() })),
    { label: ';', shift: ':', key: 'Semicolon' },
    { label: "'", shift: '"', key: 'Quote' },
    { label: '↵ Enter', key: 'Enter', special: true, w: 1.85 },
  ],
  [
    { label: '⇧ Shift', mod: 'shift', special: true, w: 1.75 },
    ...'zxcvbnm'.split('').map((c) => ({ label: c, shift: c.toUpperCase(), key: 'Key' + c.toUpperCase() })),
    { label: ',', shift: '<', key: 'Comma' },
    { label: '.', shift: '>', key: 'Period' },
    { label: '/', shift: '?', key: 'Slash' },
    { label: '▲', key: 'ArrowUp', special: true, w: 1.1 },
    { label: '⇧', mod: 'shift', special: true, w: 1.2 },
  ],
  [
    { label: 'Ctrl', mod: 'ctrl', special: true, w: 1.25 },
    { label: 'Fn', layer: 'fn', special: true, w: 1.05 },
    { label: '⊞ Win', mod: 'meta', special: true, w: 1.15 },
    { label: 'Alt', mod: 'alt', special: true, w: 1.15 },
    { label: '🌐 عربي', layer: 'ar', special: true, w: 1.3 },
    { id: 'space', label: 'Space  •  Swipe to move cursor', key: 'Space', w: 4.5 },
    { label: '◀', key: 'ArrowLeft', special: true, w: 1.1 },
    { label: '▼', key: 'ArrowDown', special: true, w: 1.1 },
    { label: '▶', key: 'ArrowRight', special: true, w: 1.1 },
  ],
];

const FN_ROWS = [
  ['Esc', 'F1', 'F2', 'F3', 'F4', 'F5', 'F6', 'F7', 'F8', 'F9', 'F10', 'F11', 'F12'].map((k) => ({
    label: k,
    key: k === 'Esc' ? 'Escape' : k,
    special: true,
  })),
  [
    { label: 'PrtSc', key: 'PrintScreen', special: true },
    { label: 'Insert', key: 'Insert', special: true },
    { label: 'Delete', key: 'Delete', special: true },
    { label: '7', key: 'Digit7' },
    { label: '8', key: 'Digit8' },
    { label: '9', key: 'Digit9' },
    { label: '/', key: 'Slash' },
    { label: '⌫', key: 'Backspace', special: true, w: 1.4 },
  ],
  [
    { label: '🔇 كتم', key: 'VolumeMute', special: true, w: 1.3 },
    { label: '🔉 خفض', key: 'VolumeDown', special: true, w: 1.3 },
    { label: '🔊 رفع', key: 'VolumeUp', special: true, w: 1.3 },
    { label: '4', key: 'Digit4' },
    { label: '5', key: 'Digit5' },
    { label: '6', key: 'Digit6' },
    { label: '*', shift: '*', key: 'Digit8' },
  ],
  [
    { label: '⏮ السابق', key: 'MediaPrevTrack', special: true, w: 1.3 },
    { label: '⏯ تشغيل', key: 'MediaPlayPause', special: true, w: 1.3 },
    { label: '⏭ التالي', key: 'MediaNextTrack', special: true, w: 1.3 },
    { label: '1', key: 'Digit1' },
    { label: '2', key: 'Digit2' },
    { label: '3', key: 'Digit3' },
    { label: '-', key: 'Minus' },
  ],
  [
    { label: 'Win+D سطح المكتب', combo: 'meta+KeyD', special: true, w: 2 },
    { label: 'Alt+Tab تبديل النوافذ', combo: 'alt+Tab', special: true, w: 2 },
    { label: '0', key: 'Digit0', w: 1.5 },
    { label: '.', key: 'Period' },
    { label: '+', key: 'Equal' },
  ],
  [
    { label: '🌐 رجوع للكيبورد', layer: 'ar', special: true, w: 2.5 },
    { id: 'space', label: 'مسافة', key: 'Space', w: 4.5 },
    { label: '◀', key: 'ArrowLeft', special: true },
    { label: '▼', key: 'ArrowDown', special: true },
    { label: '▶', key: 'ArrowRight', special: true },
  ],
];

// State
let currentLayer = 'ar';
let surfaceMode = 'pc'; // 'pc' | 'ime'
let capsLock = false;
const latchedMods = { ctrl: false, alt: false, shift: false, meta: false };

// Simulated Windows 11 Notepad Buffer & Undo Stack
let docText = 'بسم الله الرحمن الرحيم\nجرب الكتابة من شاشة الهاتف المقابلة — أي حرف تكتبه سيُشفّر وينتقل فوراً إلى اللابتوب!\n';
let cursorPos = docText.length;
let clipboardBuf = '';
const undoStack = [];

let serverState = {
  paired: false,
  pendingPinApproval: false,
  pairingPin: '--- ---',
  links: { usb: true, wifi: true, bluetooth: true },
  preference: 'auto',
  activeMedium: null,
  standbyMediums: [],
  rttMs: '--',
  encryptedPackets: 0,
  dedupDroppedCount: 0,
};

// Elements
const phoneSetupScreen = document.getElementById('phoneSetupScreen');
const phoneKeyboardScreen = document.getElementById('phoneKeyboardScreen');
const phoneScreenIndicator = document.getElementById('phoneScreenIndicator');
const pcCustomKeyboard = document.getElementById('pcCustomKeyboard');
const systemImeMode = document.getElementById('systemImeMode');
const imeLiveInput = document.getElementById('imeLiveInput');
const winEditorSurface = document.getElementById('winEditorSurface');
const cipherTelemetry = document.getElementById('cipherTelemetry');
const dedupTelemetry = document.getElementById('dedupTelemetry');

// Relative WebSocket connection (works seamlessly through Arena HTTPS preview proxy)
const wsProto = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
const ws = new WebSocket(`${wsProto}//${window.location.host}/ws`);

ws.addEventListener('open', () => {
  // Auto-pair via QR token after 300ms so user can immediately start typing OR test unpairing/PIN pairing!
  setTimeout(() => {
    ws.send(JSON.stringify({ cmd: 'pair_via_qr' }));
    switchPhoneScreen('keyboard');
  }, 350);
});

ws.addEventListener('message', (ev) => {
  const msg = JSON.parse(ev.data);
  if (msg.type === 'state_sync') {
    serverState = msg;
    renderSyncUi();
  } else if (msg.type === 'host_injected') {
    serverState.encryptedPackets = msg.encryptedPackets;
    serverState.activeMedium = msg.activeMedium;
    serverState.rttMs = msg.rttMs;
    applyHostInjectedEvent(msg.event);
    cipherTelemetry.textContent =
      `ChaCha20-Poly1305 [${msg.activeMedium.toUpperCase()} • ${msg.rttMs}ms]: ${msg.cipherHexSample}`;
    dedupTelemetry.textContent =
      `الحزم المشفرة: ${serverState.encryptedPackets} | منع التكرار عند التبديل: ${serverState.dedupDroppedCount}`;
    renderSyncUi();
  }
});

// Load QR & Host Info
fetch('/api/bootstrap')
  .then((r) => r.json())
  .then((data) => {
    document.getElementById('winQrContainer').innerHTML = data.host.qrSvg;
    document.getElementById('discoveredPcName').textContent = data.host.deviceName;
  });

function sendInputToHost(kind, value, modifiers = {}) {
  if (ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ cmd: 'input_event', kind, value, modifiers }));
  }
}

function applyHostInjectedEvent(ev) {
  undoStack.push({ docText, cursorPos });
  if (undoStack.length > 80) undoStack.shift();

  if (ev.kind === 'text') {
    docText = docText.slice(0, cursorPos) + ev.value + docText.slice(cursorPos);
    cursorPos += ev.value.length;
  } else if (ev.kind === 'shortcut') {
    handleWinShortcut(ev.value);
  } else if (ev.kind === 'key') {
    if (ev.modifiers && ev.modifiers.ctrl) {
      handleWinShortcut(`ctrl+${ev.value}`);
    } else {
      switch (ev.value) {
        case 'Backspace':
          if (cursorPos > 0) {
            docText = docText.slice(0, cursorPos - 1) + docText.slice(cursorPos);
            cursorPos -= 1;
          }
          break;
        case 'Delete':
          if (cursorPos < docText.length) {
            docText = docText.slice(0, cursorPos) + docText.slice(cursorPos + 1);
          }
          break;
        case 'Enter':
          docText = docText.slice(0, cursorPos) + '\n' + docText.slice(cursorPos);
          cursorPos += 1;
          break;
        case 'Tab':
          docText = docText.slice(0, cursorPos) + '    ' + docText.slice(cursorPos);
          cursorPos += 4;
          break;
        case 'Space':
          docText = docText.slice(0, cursorPos) + ' ' + docText.slice(cursorPos);
          cursorPos += 1;
          break;
        case 'ArrowLeft':
          cursorPos = Math.max(0, cursorPos - 1);
          break;
        case 'ArrowRight':
          cursorPos = Math.min(docText.length, cursorPos + 1);
          break;
        case 'Home':
          cursorPos = 0;
          break;
        case 'End':
          cursorPos = docText.length;
          break;
        default:
          break;
      }
    }
  }
  renderWindowsEditor();
}

function handleWinShortcut(combo) {
  switch (combo) {
    case 'ctrl+KeyC':
      clipboardBuf = docText;
      break;
    case 'ctrl+KeyX':
      clipboardBuf = docText;
      docText = '';
      cursorPos = 0;
      break;
    case 'ctrl+KeyV':
      if (clipboardBuf) {
        docText = docText.slice(0, cursorPos) + clipboardBuf + docText.slice(cursorPos);
        cursorPos += clipboardBuf.length;
      }
      break;
    case 'ctrl+KeyA':
      clipboardBuf = docText;
      break;
    case 'ctrl+KeyZ':
      if (undoStack.length >= 2) {
        undoStack.pop(); // current push
        const prev = undoStack.pop();
        docText = prev.docText;
        cursorPos = prev.cursorPos;
      }
      break;
    default:
      break;
  }
}

function renderWindowsEditor() {
  const before = escapeHtml(docText.slice(0, cursorPos));
  const after = escapeHtml(docText.slice(cursorPos));
  winEditorSurface.innerHTML = `${before}<span class="cursor-caret"></span>${after}`;
}

function escapeHtml(s) {
  return s
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

function renderKeyboard() {
  const rows =
    currentLayer === 'ar' ? ARABIC_101_ROWS :
    currentLayer === 'en' ? ENGLISH_QWERTY_ROWS :
    FN_ROWS;

  const shiftEffective = latchedMods.shift !== capsLock;
  pcCustomKeyboard.innerHTML = '';

  rows.forEach((row, rIdx) => {
    const rowEl = document.createElement('div');
    rowEl.className = 'kb-row' + (rIdx === 0 ? ' utility-row' : '');

    row.forEach((spec) => {
      const btn = document.createElement('div');
      const isModLatched = spec.mod && latchedMods[spec.mod];
      const isCapsActive = spec.key === 'CapsLock' && capsLock;

      btn.className =
        'keycap' +
        (spec.special ? ' special' : '') +
        (isModLatched || isCapsActive ? ' latched' : '');
      btn.style.flex = String(spec.w || 1);

      const mainText = shiftEffective && spec.shift ? spec.shift : spec.label;
      const subText = spec.sub || (spec.shift && !shiftEffective ? spec.shift : '');

      if (subText) {
        const sub = document.createElement('span');
        sub.className = 'sub-label';
        sub.textContent = subText;
        btn.appendChild(sub);
      }
      const center = document.createElement('span');
      center.textContent = mainText;
      btn.appendChild(center);

      if (spec.id === 'space') {
        let startX = null;
        let swiped = false;
        btn.addEventListener('pointerdown', (e) => {
          startX = e.clientX;
          swiped = false;
          btn.setPointerCapture(e.pointerId);
        });
        btn.addEventListener('pointermove', (e) => {
          if (startX === null) return;
          const dx = e.clientX - startX;
          if (Math.abs(dx) >= 22) {
            swiped = true;
            startX = e.clientX;
            sendInputToHost('key', dx > 0 ? 'ArrowRight' : 'ArrowLeft');
          }
        });
        btn.addEventListener('pointerup', () => {
          if (!swiped && startX !== null) {
            sendInputToHost('key', 'Space');
          }
          startX = null;
        });
      } else {
        btn.addEventListener('pointerdown', (e) => {
          e.preventDefault();
          handleSpecPress(spec);
        });
      }

      rowEl.appendChild(btn);
    });

    pcCustomKeyboard.appendChild(rowEl);
  });
}

function handleSpecPress(spec) {
  if (spec.layer) {
    currentLayer = spec.layer;
    renderKeyboard();
    return;
  }
  if (spec.mod) {
    latchedMods[spec.mod] = !latchedMods[spec.mod];
    renderKeyboard();
    return;
  }
  if (spec.key === 'CapsLock') {
    capsLock = !capsLock;
    renderKeyboard();
    return;
  }
  if (spec.combo) {
    sendInputToHost('shortcut', spec.combo);
    clearLatches();
    return;
  }

  const hasCtrlOrAlt = latchedMods.ctrl || latchedMods.alt || latchedMods.meta;
  const shiftEffective = latchedMods.shift !== capsLock;

  if (spec.special) {
    sendInputToHost('key', spec.key, { ...latchedMods });
  } else if (hasCtrlOrAlt) {
    sendInputToHost('key', spec.key, { ...latchedMods });
  } else {
    const outChar = shiftEffective && spec.shift ? spec.shift : spec.label;
    sendInputToHost('text', outChar);
  }
  clearLatches();
}

function clearLatches() {
  let changed = false;
  for (const k of Object.keys(latchedMods)) {
    if (latchedMods[k]) {
      latchedMods[k] = false;
      changed = true;
    }
  }
  if (changed) renderKeyboard();
}

function switchPhoneScreen(target) {
  const isKb = target === 'keyboard';
  phoneSetupScreen.classList.toggle('hidden', isKb);
  phoneKeyboardScreen.classList.toggle('hidden', !isKb);
  phoneScreenIndicator.textContent = isKb
    ? 'الشاشة 2: كيبورد يملأ الشاشة بالكامل'
    : 'الشاشة 1: إعداد البرنامج';
}

function renderSyncUi() {
  // Hardware simulator chips
  const usbBtn = document.getElementById('toggleUsbBtn');
  const wifiBtn = document.getElementById('toggleWifiBtn');
  const btBtn = document.getElementById('toggleBtBtn');

  usbBtn.className = 'hw-chip' + (serverState.links.usb ? ' active-usb' : '');
  usbBtn.textContent = `⚡ سلك USB: ${serverState.links.usb ? 'متصل' : 'مفصول'}`;

  wifiBtn.className = 'hw-chip' + (serverState.links.wifi ? ' active-wifi' : '');
  wifiBtn.textContent = `📶 Wi-Fi: ${serverState.links.wifi ? 'متصل' : 'مفصول'}`;

  btBtn.className = 'hw-chip' + (serverState.links.bluetooth ? ' active-bt' : '');
  btBtn.textContent = `🔵 Bluetooth: ${serverState.links.bluetooth ? 'متصل' : 'مفصول'}`;

  // Medium pills on Setup card
  const pillsEl = document.getElementById('hostMediumPills');
  pillsEl.innerHTML = '';
  if (serverState.links.usb) {
    pillsEl.innerHTML += '<span class="mini-pill" style="background:rgba(16,185,129,0.2);color:#10b981;">⚡ USB (0.4ms)</span>';
  }
  if (serverState.links.wifi) {
    pillsEl.innerHTML += '<span class="mini-pill" style="background:rgba(56,189,248,0.2);color:#38bdf8;">📶 Wi-Fi 192.168.1.24</span>';
  }
  if (serverState.links.bluetooth) {
    pillsEl.innerHTML += '<span class="mini-pill" style="background:rgba(129,140,248,0.2);color:#818cf8;">🔵 BT RFCOMM</span>';
  }

  document.getElementById('trustedBadge').classList.toggle('hidden', !serverState.paired);
  document.getElementById('unpairHostBtn').classList.toggle('hidden', !serverState.paired);
  document.getElementById('connectHostCardBtn').textContent = serverState.paired ? 'فتح الكيبورد' : 'إقران واتصال';

  // Windows PIN Prompt
  const pinPrompt = document.getElementById('winPinPrompt');
  pinPrompt.classList.toggle('hidden', !serverState.pendingPinApproval);
  document.getElementById('winPinCode').textContent = serverState.pairingPin;

  // Top Keyboard Status Strip & Windows Host Badge
  const dot = document.getElementById('kbStatusDot');
  const txt = document.getElementById('kbStatusText');
  const winBadge = document.getElementById('winHostBadge');

  const mediumNames = { usb: '⚡ USB', wifi: '📶 Wi-Fi', bluetooth: '🔵 Bluetooth' };
  if (serverState.activeMedium) {
    dot.className = `status-dot ${serverState.activeMedium}`;
    const stby = serverState.standbyMediums.length
      ? ` • احتياطي: ${serverState.standbyMediums.map((m) => mediumNames[m]).join(' ، ')}`
      : '';
    txt.textContent = `DESKTOP-WIN11-PRO • ${mediumNames[serverState.activeMedium]} (${serverState.rttMs}ms)${stby}`;
    winBadge.textContent = `متصل بـ Galaxy S24 Ultra عبر ${mediumNames[serverState.activeMedium]}`;
    winBadge.style.color = '#10b981';
  } else {
    dot.className = 'status-dot';
    txt.textContent = serverState.paired ? 'كل الوسائط مفصولة — أعد تفعيل USB أو Wi-Fi أو BT' : 'غير مقترن — افتح الإعداد';
    winBadge.textContent = serverState.pendingPinApproval ? 'بانتظار موافقة رمز PIN...' : 'غير متصل';
    winBadge.style.color = '#94a3b8';
  }

  dedupTelemetry.textContent =
    `الحزم المشفرة: ${serverState.encryptedPackets} | منع التكرار عند التبديل: ${serverState.dedupDroppedCount}`;
}

// Wire UI Buttons
document.getElementById('goToKeyboardBtn').addEventListener('click', () => switchPhoneScreen('keyboard'));
document.getElementById('backToSetupBtn').addEventListener('click', () => switchPhoneScreen('setup'));

document.getElementById('scanQrSimBtn').addEventListener('click', () => {
  ws.send(JSON.stringify({ cmd: 'pair_via_qr' }));
  switchPhoneScreen('keyboard');
});

document.getElementById('requestPinBtn').addEventListener('click', () => {
  ws.send(JSON.stringify({ cmd: 'request_pin_pair' }));
});

document.getElementById('connectHostCardBtn').addEventListener('click', () => {
  if (serverState.paired) {
    switchPhoneScreen('keyboard');
  } else {
    ws.send(JSON.stringify({ cmd: 'request_pin_pair' }));
  }
});

document.getElementById('acceptPinBtn').addEventListener('click', () => {
  ws.send(JSON.stringify({ cmd: 'host_pin_decision', accept: true }));
  switchPhoneScreen('keyboard');
});

document.getElementById('rejectPinBtn').addEventListener('click', () => {
  ws.send(JSON.stringify({ cmd: 'host_pin_decision', accept: false }));
});

document.getElementById('unpairHostBtn').addEventListener('click', () => {
  ws.send(JSON.stringify({ cmd: 'unpair' }));
});

document.getElementById('toggleUsbBtn').addEventListener('click', () => {
  ws.send(JSON.stringify({ cmd: 'toggle_link', medium: 'usb', enabled: !serverState.links.usb }));
});
document.getElementById('toggleWifiBtn').addEventListener('click', () => {
  ws.send(JSON.stringify({ cmd: 'toggle_link', medium: 'wifi', enabled: !serverState.links.wifi }));
});
document.getElementById('toggleBtBtn').addEventListener('click', () => {
  ws.send(JSON.stringify({ cmd: 'toggle_link', medium: 'bluetooth', enabled: !serverState.links.bluetooth }));
});

document.getElementById('expandPhoneBtn').addEventListener('click', (e) => {
  const full = document.body.classList.toggle('phone-fullscreen');
  if (full) switchPhoneScreen('keyboard');
  e.currentTarget.textContent = full ? '◫ عرض الهاتف واللابتوب معاً' : '⛶ ملء الشاشة بالكيبورد';
});

document.getElementById('modeToggleBtn').addEventListener('click', (e) => {
  surfaceMode = surfaceMode === 'pc' ? 'ime' : 'pc';
  pcCustomKeyboard.classList.toggle('hidden', surfaceMode !== 'pc');
  systemImeMode.classList.toggle('hidden', surfaceMode !== 'ime');
  e.currentTarget.textContent = surfaceMode === 'pc' ? '📱 كيبورد الهاتف / الإملاء' : '⌨️ كيبورد PC كامل';
  if (surfaceMode === 'ime') imeLiveInput.focus();
});

let prevImeValue = '';
imeLiveInput.addEventListener('input', () => {
  const cur = imeLiveInput.value;
  if (cur.length > prevImeValue.length && cur.startsWith(prevImeValue)) {
    const added = cur.slice(prevImeValue.length);
    sendInputToHost('text', added);
  } else if (cur.length < prevImeValue.length) {
    const removed = prevImeValue.length - cur.length;
    for (let i = 0; i < removed; i++) sendInputToHost('key', 'Backspace');
  }
  prevImeValue = cur;
});

document.querySelectorAll('[data-combo]').forEach((btn) => {
  btn.addEventListener('click', () => sendInputToHost('shortcut', btn.dataset.combo));
});
document.querySelectorAll('[data-rawkey]').forEach((btn) => {
  btn.addEventListener('click', () => sendInputToHost('key', btn.dataset.rawkey));
});

document.querySelectorAll('#prefChips .pref-chip').forEach((chip) => {
  chip.addEventListener('click', () => {
    document.querySelectorAll('#prefChips .pref-chip').forEach((c) => c.classList.remove('selected'));
    chip.classList.add('selected');
    ws.send(JSON.stringify({ cmd: 'set_preference', preference: chip.dataset.pref }));
  });
});

document.getElementById('clearNotepadBtn').addEventListener('click', () => {
  docText = '';
  cursorPos = 0;
  renderWindowsEditor();
});

renderKeyboard();
renderWindowsEditor();
