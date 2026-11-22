import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import { WebSocketServer } from 'ws';
import QRCode from 'qrcode';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PUBLIC_DIR = path.join(__dirname, 'public');
const PORT = Number(process.env.PORT || 3000);

const PKCS8_X25519_PREFIX = Buffer.from('302e020100300506032b656e04220420', 'hex');
const SPKI_X25519_PREFIX = Buffer.from('302a300506032b656e032100', 'hex');

function createX25519Identity(seedHex) {
  const seed = seedHex ? Buffer.from(seedHex, 'hex') : crypto.randomBytes(32);
  const privKey = crypto.createPrivateKey({
    key: Buffer.concat([PKCS8_X25519_PREFIX, seed]),
    format: 'der',
    type: 'pkcs8',
  });
  const pubKey = crypto.createPublicKey(privKey);
  const spki = pubKey.export({ format: 'der', type: 'spki' });
  const pubRaw = spki.subarray(SPKI_X25519_PREFIX.length);
  const deviceId = crypto.createHash('sha256').update(pubRaw).digest('hex').slice(0, 16);
  return { privKey, pubRaw, pubHex: pubRaw.toString('hex'), deviceId };
}

function computePairingPin(pubA, pubB) {
  const [minPub, maxPub] = Buffer.compare(pubA, pubB) <= 0 ? [pubA, pubB] : [pubB, pubA];
  const digest = crypto
    .createHash('sha256')
    .update(Buffer.from('KC-PIN-V1', 'utf8'))
    .update(minPub)
    .update(maxPub)
    .digest();
  const num = digest.readUInt32BE(0) % 1_000_000;
  return `${String(Math.floor(num / 1000)).padStart(3, '0')} ${String(num % 1000).padStart(3, '0')}`;
}

function deriveSessionKey(privKey, peerPubRaw, clientNonce, hostNonce) {
  const peerPubKey = crypto.createPublicKey({
    key: Buffer.concat([SPKI_X25519_PREFIX, peerPubRaw]),
    format: 'der',
    type: 'spki',
  });
  const shared = crypto.diffieHellman({ privateKey: privKey, publicKey: peerPubKey });
  const salt = Buffer.concat([clientNonce, hostNonce]);
  return Buffer.from(crypto.hkdfSync('sha256', shared, salt, Buffer.from('KeyConnect-Session-v1'), 32));
}

function sealAead(sessionKey, sessionId8, seq, obj) {
  const nonce = Buffer.alloc(12);
  nonce.set([0x43, 0x32, 0x48, 0x01], 0);
  nonce.writeBigUInt64BE(BigInt(seq), 4);
  const plain = Buffer.from(JSON.stringify(obj), 'utf8');
  const cipher = crypto.createCipheriv('chacha20-poly1305', sessionKey, nonce, { authTagLength: 16 });
  cipher.setAAD(sessionId8, { plaintextLength: plain.length });
  return Buffer.concat([cipher.update(plain), cipher.final(), cipher.getAuthTag()]);
}

function openAead(sessionKey, sessionId8, seq, ciphertextWithTag) {
  const nonce = Buffer.alloc(12);
  nonce.set([0x43, 0x32, 0x48, 0x01], 0);
  nonce.writeBigUInt64BE(BigInt(seq), 4);
  const ct = ciphertextWithTag.subarray(0, ciphertextWithTag.length - 16);
  const tag = ciphertextWithTag.subarray(ciphertextWithTag.length - 16);
  const decipher = crypto.createDecipheriv('chacha20-poly1305', sessionKey, nonce, { authTagLength: 16 });
  decipher.setAAD(sessionId8, { plaintextLength: ct.length });
  decipher.setAuthTag(tag);
  const plain = Buffer.concat([decipher.update(ct), decipher.final()]);
  return JSON.parse(plain.toString('utf8'));
}

class SlidingDeduplicator {
  constructor() {
    this.highestId = 0n;
    this.windowMask = 0n;
  }
  accept(eventId) {
    const id = BigInt(eventId);
    if (id <= 0n) return false;
    if (id > this.highestId) {
      const shift = id - this.highestId;
      this.windowMask = shift >= 128n ? 1n : ((this.windowMask << shift) | 1n) & ((1n << 128n) - 1n);
      this.highestId = id;
      return true;
    }
    const diff = this.highestId - id;
    if (diff >= 128n) return false;
    const bit = 1n << diff;
    if ((this.windowMask & bit) !== 0n) return false;
    this.windowMask |= bit;
    return true;
  }
}

// Simulated Host & Phone cryptographic identities per browser preview session
const hostIdentity = createX25519Identity();
const phoneIdentity = createX25519Identity();
const hostQrToken = crypto.randomBytes(16).toString('hex');
const pairingPin = computePairingPin(phoneIdentity.pubRaw, hostIdentity.pubRaw);
const qrUri = `keyconnect://pair?id=${hostIdentity.deviceId}&name=DESKTOP-WIN11-PRO&ips=192.168.1.24,127.0.0.1&port=45821&pub=${hostIdentity.pubHex}&token=${hostQrToken}`;

let cachedQrSvg = '';
QRCode.toString(qrUri, { type: 'svg', margin: 1, color: { dark: '#0f172a', light: '#ffffff' } })
  .then((svg) => { cachedQrSvg = svg; });

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
};

const server = http.createServer((req, res) => {
  const url = new URL(req.url || '/', 'http://0.0.0.0');
  if (url.pathname === '/api/bootstrap') {
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({
      host: {
        deviceId: hostIdentity.deviceId,
        deviceName: 'DESKTOP-WIN11-PRO',
        publicKeyHex: hostIdentity.pubHex,
        wifiIp: '192.168.1.24',
        usbIp: '127.0.0.1',
        btAddress: 'AC:DE:48:11:22:33',
        qrUri,
        qrSvg: cachedQrSvg,
      },
      phone: {
        deviceId: phoneIdentity.deviceId,
        deviceName: 'Galaxy S24 Ultra',
        publicKeyHex: phoneIdentity.pubHex,
      },
      pairingPin,
    }));
    return;
  }

  let filePath = path.join(PUBLIC_DIR, url.pathname === '/' ? 'index.html' : url.pathname);
  if (!filePath.startsWith(PUBLIC_DIR)) {
    res.writeHead(403);
    res.end('Forbidden');
    return;
  }

  fs.readFile(filePath, (err, content) => {
    if (err) {
      res.writeHead(404);
      res.end('Not found');
      return;
    }
    const ext = path.extname(filePath);
    res.writeHead(200, { 'Content-Type': MIME_TYPES[ext] || 'text/plain; charset=utf-8' });
    res.end(content);
  });
});

const wss = new WebSocketServer({ server, path: '/ws' });

wss.on('connection', (ws) => {
  const clientNonce = crypto.randomBytes(16);
  const hostNonce = crypto.randomBytes(16);
  const sessionId = crypto.randomBytes(8);
  const clientSessionKey = deriveSessionKey(phoneIdentity.privKey, hostIdentity.pubRaw, clientNonce, hostNonce);
  const hostSessionKey = deriveSessionKey(hostIdentity.privKey, phoneIdentity.pubRaw, clientNonce, hostNonce);
  const dedup = new SlidingDeduplicator();

  let seqCounter = 0;
  let eventCounter = 0;
  let dedupDroppedCount = 0;
  let paired = false;
  let pendingPinApproval = false;

  // Simulated physical link availability
  const links = { usb: true, wifi: true, bluetooth: true };
  let preference = 'auto';

  function pickActiveMedium() {
    if (!paired) return null;
    if (preference === 'usb') return links.usb ? 'usb' : null;
    if (preference === 'wifi') return links.wifi ? 'wifi' : null;
    if (preference === 'bluetooth') return links.bluetooth ? 'bluetooth' : null;
    if (links.usb) return 'usb';
    if (links.wifi) return 'wifi';
    if (links.bluetooth) return 'bluetooth';
    return null;
  }

  function rttForMedium(m) {
    if (m === 'usb') return '0.4';
    if (m === 'wifi') return '1.8';
    if (m === 'bluetooth') return '5.1';
    return '--';
  }

  function pushState() {
    const active = pickActiveMedium();
    const standbys = Object.entries(links)
      .filter(([k, up]) => up && k !== active)
      .map(([k]) => k);
    ws.send(JSON.stringify({
      type: 'state_sync',
      paired,
      pendingPinApproval,
      pairingPin,
      links,
      preference,
      activeMedium: active,
      standbyMediums: standbys,
      rttMs: rttForMedium(active),
      encryptedPackets: seqCounter,
      dedupDroppedCount,
    }));
  }

  pushState();

  ws.on('message', (raw) => {
    let msg;
    try {
      msg = JSON.parse(raw.toString());
    } catch {
      return;
    }

    if (msg.cmd === 'pair_via_qr') {
      paired = true;
      pendingPinApproval = false;
      pushState();
      return;
    }

    if (msg.cmd === 'request_pin_pair') {
      pendingPinApproval = true;
      pushState();
      return;
    }

    if (msg.cmd === 'host_pin_decision') {
      pendingPinApproval = false;
      paired = Boolean(msg.accept);
      pushState();
      return;
    }

    if (msg.cmd === 'unpair') {
      paired = false;
      pendingPinApproval = false;
      pushState();
      return;
    }

    if (msg.cmd === 'toggle_link') {
      if (msg.medium in links) {
        const prevActive = pickActiveMedium();
        links[msg.medium] = Boolean(msg.enabled);
        const nextActive = pickActiveMedium();

        // Simulate realistic in-flight packet replay across failover link to demonstrate deduplication!
        if (paired && prevActive && nextActive && prevActive !== nextActive && eventCounter > 0) {
          seqCounter += 1;
          const replayCipher = sealAead(clientSessionKey, sessionId, seqCounter, {
            type: 'replay_probe',
            event_id: eventCounter,
          });
          const replayPlain = openAead(hostSessionKey, sessionId, seqCounter, replayCipher);
          if (!dedup.accept(replayPlain.event_id)) {
            dedupDroppedCount += 1;
          }
        }
        pushState();
      }
      return;
    }

    if (msg.cmd === 'set_preference') {
      preference = msg.preference;
      pushState();
      return;
    }

    if (msg.cmd === 'input_event') {
      const active = pickActiveMedium();
      if (!active) return;

      eventCounter += 1;
      seqCounter += 1;

      const wirePayload = {
        event_id: eventCounter,
        medium: active,
        kind: msg.kind,        // 'text' | 'key' | 'shortcut'
        value: msg.value,
        modifiers: msg.modifiers || {},
      };

      // Encrypt on Phone side (ChaCha20-Poly1305) & Decrypt + Authenticate on Windows Host side!
      const sealed = sealAead(clientSessionKey, sessionId, seqCounter, wirePayload);
      const opened = openAead(hostSessionKey, sessionId, seqCounter, sealed);

      if (dedup.accept(opened.event_id)) {
        ws.send(JSON.stringify({
          type: 'host_injected',
          event: opened,
          cipherHexSample: sealed.subarray(0, 14).toString('hex') + '…',
          encryptedPackets: seqCounter,
          activeMedium: active,
          rttMs: rttForMedium(active),
        }));
      }
    }
  });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`KeyConnect Live Simulator & Web Companion listening on http://0.0.0.0:${PORT}`);
});
