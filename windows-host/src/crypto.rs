use chacha20poly1305::{
    aead::{Aead, KeyInit, Payload},
    ChaCha20Poly1305, Key, Nonce,
};
use hkdf::Hkdf;
use rand_core::{OsRng, RngCore};
use sha2::{Digest, Sha256};
use x25519_dalek::{PublicKey, StaticSecret};

#[derive(Clone)]
pub struct StaticIdentity {
    secret: StaticSecret,
    public: PublicKey,
}

impl StaticIdentity {
    pub fn generate() -> Self {
        let secret = StaticSecret::random_from_rng(OsRng);
        let public = PublicKey::from(&secret);
        Self { secret, public }
    }

    pub fn from_secret_bytes(bytes: [u8; 32]) -> Self {
        let secret = StaticSecret::from(bytes);
        let public = PublicKey::from(&secret);
        Self { secret, public }
    }

    pub fn secret_bytes(&self) -> [u8; 32] {
        self.secret.to_bytes()
    }

    pub fn public_bytes(&self) -> [u8; 32] {
        *self.public.as_bytes()
    }

    pub fn public_hex(&self) -> String {
        hex::encode(self.public.as_bytes())
    }

    pub fn device_id(&self) -> String {
        derive_device_id(self.public.as_bytes())
    }

    pub fn diffie_hellman(&self, peer_public_bytes: &[u8; 32]) -> [u8; 32] {
        let peer_pub = PublicKey::from(*peer_public_bytes);
        *self.secret.diffie_hellman(&peer_pub).as_bytes()
    }
}

pub fn random_bytes<const N: usize>() -> [u8; N] {
    let mut buf = [0u8; N];
    OsRng.fill_bytes(&mut buf);
    buf
}

pub fn derive_device_id(pub_bytes: &[u8; 32]) -> String {
    let digest = Sha256::digest(pub_bytes);
    hex::encode(&digest[..8])
}

/// Computes the symmetric 6-digit pairing verification code ("DDD DDD") from both static X25519 public keys.
pub fn compute_pairing_pin(pub_a: &[u8; 32], pub_b: &[u8; 32]) -> String {
    let (min_pub, max_pub) = if pub_a <= pub_b {
        (pub_a, pub_b)
    } else {
        (pub_b, pub_a)
    };
    let mut hasher = Sha256::new();
    hasher.update(b"KC-PIN-V1");
    hasher.update(min_pub);
    hasher.update(max_pub);
    let digest = hasher.finalize();
    let num = u32::from_be_bytes([digest[0], digest[1], digest[2], digest[3]]) % 1_000_000;
    format!("{:03} {:03}", num / 1000, num % 1000)
}

pub fn derive_session_key(
    shared_secret: &[u8; 32],
    client_nonce: &[u8; 16],
    host_nonce: &[u8; 16],
) -> [u8; 32] {
    let mut salt = [0u8; 32];
    salt[..16].copy_from_slice(client_nonce);
    salt[16..].copy_from_slice(host_nonce);

    let hk = Hkdf::<Sha256>::new(Some(&salt), shared_secret);
    let mut okm = [0u8; 32];
    hk.expand(b"KeyConnect-Session-v1", &mut okm)
        .expect("32-byte HKDF output is valid");
    okm
}

#[derive(Clone, Copy)]
pub enum Direction {
    ClientToHost,
    HostToClient,
}

impl Direction {
    fn prefix(self) -> [u8; 4] {
        match self {
            Self::ClientToHost => [0x43, 0x32, 0x48, 0x01], // "C2H\x01"
            Self::HostToClient => [0x48, 0x32, 0x43, 0x01], // "H2C\x01"
        }
    }
}

#[derive(Clone)]
pub struct SessionCipher {
    cipher: ChaCha20Poly1305,
    session_id: [u8; 8],
}

impl SessionCipher {
    pub fn new(session_key: &[u8; 32], session_id: [u8; 8]) -> Self {
        let cipher = ChaCha20Poly1305::new(Key::from_slice(session_key));
        Self { cipher, session_id }
    }

    pub fn session_id(&self) -> [u8; 8] {
        self.session_id
    }

    fn build_nonce(dir: Direction, seq: u64) -> [u8; 12] {
        let mut nonce = [0u8; 12];
        nonce[..4].copy_from_slice(&dir.prefix());
        nonce[4..].copy_from_slice(&seq.to_be_bytes());
        nonce
    }

    pub fn seal(&self, dir: Direction, seq: u64, plaintext: &[u8]) -> Result<Vec<u8>, String> {
        let nonce_bytes = Self::build_nonce(dir, seq);
        let nonce = Nonce::from_slice(&nonce_bytes);
        self.cipher
            .encrypt(
                nonce,
                Payload {
                    msg: plaintext,
                    aad: &self.session_id,
                },
            )
            .map_err(|_| "AEAD encryption error".to_string())
    }

    pub fn open(&self, dir: Direction, seq: u64, ciphertext: &[u8]) -> Result<Vec<u8>, String> {
        let nonce_bytes = Self::build_nonce(dir, seq);
        let nonce = Nonce::from_slice(&nonce_bytes);
        self.cipher
            .decrypt(
                nonce,
                Payload {
                    msg: ciphertext,
                    aad: &self.session_id,
                },
            )
            .map_err(|_| "AEAD authentication failed".to_string())
    }
}

/// 128-packet sliding bitmap deduplicator so seamless failover across USB -> Wi-Fi -> Bluetooth
/// never executes the same keystroke twice.
#[derive(Debug, Default)]
pub struct SlidingDeduplicator {
    highest_id: u64,
    window_mask: u128,
}

impl SlidingDeduplicator {
    pub fn accept(&mut self, event_id: u64) -> bool {
        if event_id == 0 {
            return false;
        }
        if event_id > self.highest_id {
            let shift = event_id - self.highest_id;
            if shift >= 128 {
                self.window_mask = 1u128;
            } else {
                self.window_mask = (self.window_mask << shift) | 1u128;
            }
            self.highest_id = event_id;
            return true;
        }
        let diff = self.highest_id - event_id;
        if diff >= 128 {
            return false;
        }
        let bit = 1u128 << diff;
        if (self.window_mask & bit) != 0 {
            return false;
        }
        self.window_mask |= bit;
        true
    }

    pub fn highest_id(&self) -> u64 {
        self.highest_id
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_cross_language_rfc_vectors() {
        let client_seed: [u8; 32] =
            hex::decode("0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20")
                .unwrap()
                .try_into()
                .unwrap();
        let host_seed: [u8; 32] =
            hex::decode("2122232425262728292a2b2c2d2e2f303132333435363738393a3b3c3d3e3f40")
                .unwrap()
                .try_into()
                .unwrap();

        let client = StaticIdentity::from_secret_bytes(client_seed);
        let host = StaticIdentity::from_secret_bytes(host_seed);

        assert_eq!(
            client.public_hex(),
            "07a37cbc142093c8b755dc1b10e86cb426374ad16aa853ed0bdfc0b2b86d1c7c"
        );
        assert_eq!(
            host.public_hex(),
            "5869aff450549732cbaaed5e5df9b30a6da31cb0e5742bad5ad4a1a768f1a67b"
        );
        assert_eq!(client.device_id(), "aaa8fff703b50b22");
        assert_eq!(host.device_id(), "4457134794559182");

        let shared = host.diffie_hellman(&client.public_bytes());
        assert_eq!(
            hex::encode(shared),
            "a84dc7c3c8f058b1b2dc4cd1e9b5dc0a7987f88b6a9564cde3391fc421159e77"
        );

        let pin = compute_pairing_pin(&client.public_bytes(), &host.public_bytes());
        assert_eq!(pin, "143 368");

        let client_nonce: [u8; 16] = hex::decode("a0a1a2a3a4a5a6a7a8a9aaabacadaeaf")
            .unwrap()
            .try_into()
            .unwrap();
        let host_nonce: [u8; 16] = hex::decode("b0b1b2b3b4b5b6b7b8b9babbbcbdbebf")
            .unwrap()
            .try_into()
            .unwrap();
        let session_key = derive_session_key(&shared, &client_nonce, &host_nonce);
        assert_eq!(
            hex::encode(session_key),
            "1edf368ddd81734f4334ef64b7b646e8b2d8dd55cfdcfdfabdf157b494c29c2e"
        );

        let session_id = *b"KCSESS01";
        let cipher = SessionCipher::new(&session_key, session_id);
        let expected_sealed = hex::decode(
            "3b6c617cec95e1888fc653dadb4f6c6fae8bc2f37002e401fe2edb4de46e8e1c0f550bcd475a236c7f3ee82d960ac07687e600bdcc45fe6b5598af27a5287e41972d9eb5f6b862714864efda7d08c484743b955750cf7768a9715fe8fa3b",
        )
        .unwrap();

        let opened = cipher
            .open(Direction::ClientToHost, 1, &expected_sealed)
            .unwrap();
        assert_eq!(
            String::from_utf8(opened).unwrap(),
            r#"{"type":"text_commit","event_id":1,"text":"السلام عليكم Keyboard!"}"#
        );
    }

    #[test]
    fn test_sliding_deduplicator() {
        let mut dedup = SlidingDeduplicator::default();
        assert!(dedup.accept(1));
        assert!(dedup.accept(2));
        assert!(!dedup.accept(2));
        assert!(dedup.accept(5));
        assert!(dedup.accept(3));
        assert!(!dedup.accept(3));
    }
}
