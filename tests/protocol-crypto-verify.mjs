#!/usr/bin/env node
/**
 * KeyConnect Protocol v1 — Reference Crypto & Framing Verification Suite
 * Verifies X25519 ECDH, 6-digit deterministic PIN, HKDF-SHA256 session keys,
 * directional ChaCha20-Poly1305 AEAD with AAD, and sliding-window deduplication.
 */

import crypto from 'node:crypto';
import assert from 'node:assert/strict';

const PKCS8_X25519_PREFIX = Buffer.from('302e020100300506032b656e04220420', 'hex');
const SPKI_X25519_PREFIX = Buffer.from('302a300506032b656e032100', 'hex');

function x25519KeyFromSeed(seed32) {
  const privKey = crypto.createPrivateKey({
    key: Buffer.concat([PKCS8_X25519_PREFIX, seed32]),
    format: 'der',
    type: 'pkcs8',
  });
  const pubKey = crypto.createPublicKey(privKey);
  const spki = pubKey.export({ format: 'der', type: 'spki' });
  const pubRaw = spki.subarray(SPKI_X25519_PREFIX.length);
  return { privKey, pubKey, pubRaw };
}

function x25519PublicFromRaw(pubRaw32) {
  return crypto.createPublicKey({
    key: Buffer.concat([SPKI_X25519_PREFIX, pubRaw32]),
    format: 'der',
    type: 'spki',
  });
}

function deriveDeviceId(pubRaw32) {
  return crypto.createHash('sha256').update(pubRaw32).digest('hex').slice(0, 16);
}

function computePairingPin(pubA, pubB) {
  const cmp = Buffer.compare(pubA, pubB);
  const [minPub, maxPub] = cmp <= 0 ? [pubA, pubB] : [pubB, pubA];
  const digest = crypto
    .createHash('sha256')
    .update(Buffer.from('KC-PIN-V1', 'utf8'))
    .update(minPub)
    .update(maxPub)
    .digest();
  const num = digest.readUInt32BE(0) % 1_000_000;
  const hi = String(Math.floor(num / 1000)).padStart(3, '0');
  const lo = String(num % 1000).padStart(3, '0');
  return `${hi} ${lo}`;
}

function deriveSessionKey(sharedSecret, clientNonce16, hostNonce16) {
  const salt = Buffer.concat([clientNonce16, hostNonce16]);
  const info = Buffer.from('KeyConnect-Session-v1', 'utf8');
  return Buffer.from(crypto.hkdfSync('sha256', sharedSecret, salt, info, 32));
}

function buildNonce(direction, seqBigInt) {
  const nonce = Buffer.alloc(12);
  const prefix = direction === 'C2H' ? [0x43, 0x32, 0x48, 0x01] : [0x48, 0x32, 0x43, 0x01];
  nonce.set(prefix, 0);
  nonce.writeBigUInt64BE(BigInt(seqBigInt), 4);
  return nonce;
}

function sealFrame(sessionKey, sessionId8, direction, seq, plaintextBuf) {
  const nonce = buildNonce(direction, seq);
  const cipher = crypto.createCipheriv('chacha20-poly1305', sessionKey, nonce, { authTagLength: 16 });
  cipher.setAAD(sessionId8, { plaintextLength: plaintextBuf.length });
  const encrypted = Buffer.concat([cipher.update(plaintextBuf), cipher.final()]);
  const tag = cipher.getAuthTag();
  return Buffer.concat([encrypted, tag]);
}

function openFrame(sessionKey, sessionId8, direction, seq, ciphertextWithTag) {
  const nonce = buildNonce(direction, seq);
  const ciphertext = ciphertextWithTag.subarray(0, ciphertextWithTag.length - 16);
  const tag = ciphertextWithTag.subarray(ciphertextWithTag.length - 16);
  const decipher = crypto.createDecipheriv('chacha20-poly1305', sessionKey, nonce, { authTagLength: 16 });
  decipher.setAAD(sessionId8, { plaintextLength: ciphertext.length });
  decipher.setAuthTag(tag);
  return Buffer.concat([decipher.update(ciphertext), decipher.final()]);
}

// Sliding Window Deduplicator (matches Rust HostHub & Kotlin orchestrator ack logic)
class SlidingDeduplicator {
  constructor() {
    this.highestId = 0n;
    this.windowMask = 0n; // 128-bit sliding bitmap
  }
  accept(eventId) {
    const id = BigInt(eventId);
    if (id === 0n) return false;
    if (id > this.highestId) {
      const shift = id - this.highestId;
      if (shift >= 128n) {
        this.windowMask = 1n;
      } else {
        this.windowMask = ((this.windowMask << shift) | 1n) & ((1n << 128n) - 1n);
      }
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

// 1. Deterministic Vector Generation & Symmetry Test
const clientSeed = Buffer.from('0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20', 'hex');
const hostSeed   = Buffer.from('2122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f40', 'hex');

const client = x25519KeyFromSeed(clientSeed);
const host   = x25519KeyFromSeed(hostSeed);

const sharedFromClient = crypto.diffieHellman({
  privateKey: client.privKey,
  publicKey: x25519PublicFromRaw(host.pubRaw),
});
const sharedFromHost = crypto.diffieHellman({
  privateKey: host.privKey,
  publicKey: x25519PublicFromRaw(client.pubRaw),
});
assert.deepEqual(sharedFromClient, sharedFromHost, 'ECDH shared secrets must match');

const pinClient = computePairingPin(client.pubRaw, host.pubRaw);
const pinHost   = computePairingPin(host.pubRaw, client.pubRaw);
assert.equal(pinClient, pinHost, '6-digit PIN must be order-independent');

const clientNonce = Buffer.from('a0a1a2a3a4a5a6a7a8a9aaabacadaeaf', 'hex');
const hostNonce   = Buffer.from('b0b1b2b3b4b5b6b7b8b9babbbcbdbebf', 'hex');
const sessionId   = Buffer.from('4b43534553533031', 'hex'); // "KCSESS01"

const sessionKey = deriveSessionKey(sharedFromClient, clientNonce, hostNonce);

const samplePayload = JSON.stringify({
  type: 'text_commit',
  event_id: 1,
  text: 'السلام عليكم Keyboard!',
});
const sealed = sealFrame(sessionKey, sessionId, 'C2H', 1n, Buffer.from(samplePayload, 'utf8'));
const opened = openFrame(sessionKey, sessionId, 'C2H', 1n, sealed).toString('utf8');
assert.equal(opened, samplePayload, 'Decrypted Arabic/English payload must match');

// Tamper test: wrong direction or modified byte must throw
assert.throws(() => openFrame(sessionKey, sessionId, 'H2C', 1n, sealed), 'Direction reflection must be rejected');
const tampered = Buffer.from(sealed);
tampered[5] ^= 0xff;
assert.throws(() => openFrame(sessionKey, sessionId, 'C2H', 1n, tampered), 'Tampered ciphertext must fail AEAD');

// Failover deduplication test (USB -> Wi-Fi -> Bluetooth replay)
const dedup = new SlidingDeduplicator();
assert.equal(dedup.accept(1), true);
assert.equal(dedup.accept(2), true);
assert.equal(dedup.accept(2), false, 'Duplicate replay across Wi-Fi failover must be ignored');
assert.equal(dedup.accept(5), true);
assert.equal(dedup.accept(3), true, 'Out-of-order UDP packet within window must be accepted once');
assert.equal(dedup.accept(3), false, 'Second arrival of packet 3 must be dropped');

console.log('=== ALL PROTOCOL & CRYPTO VERIFICATION TESTS PASSED ===');
console.log(JSON.stringify({
  client_pub_hex: client.pubRaw.toString('hex'),
  host_pub_hex: host.pubRaw.toString('hex'),
  client_id: deriveDeviceId(client.pubRaw),
  host_id: deriveDeviceId(host.pubRaw),
  shared_secret_hex: sharedFromClient.toString('hex'),
  pin: pinClient,
  session_key_hex: sessionKey.toString('hex'),
  sealed_sample_hex: sealed.toString('hex'),
}, null, 2));
