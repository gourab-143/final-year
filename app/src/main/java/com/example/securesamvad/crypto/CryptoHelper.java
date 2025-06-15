package com.example.securesamvad.crypto;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import com.google.firebase.auth.FirebaseAuth;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.KeyAgreement;

/**
 * Per‑user E2EE key helper
 *
 * • a unique key‑pair is generated once for each Firebase‑UID on the device
 * • private key is stored in Android Keystore; public key cached in SharedPreferences
 * • tries X25519 (API 31+), falls back to EC P‑256 on older devices
 * • survives logout / re‑login (alias is based on UID)
 */
public final class CryptoHelper {

    private static final String PREF_NAME = "crypto_pref";
    private static final String PREF_PUB  = "PUB_KEY_";          // suffix with uid

    private static final String ALG_X25519 = "X25519";
    private static final String ALG_EC     = "EC";
    private static final String CURVE_FALLBACK = "secp256r1";

    private static final String PROVIDER = "AndroidKeyStore";

    private CryptoHelper() { /* no‑instance */ }

    /* ─────────────────────  PUBLIC  ───────────────────── */

    /** Returns (and if necessary, generates) this device’s public key for the signed‑in UID */
    public static String getMyPublicKey(Context ctx) throws Exception {

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) throw new IllegalStateException("User not signed‑in");

        String alias   = aliasForUid(uid);        // keystore alias
        String prefKey = PREF_PUB + uid;          // shared‑prefs key

        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String cached = sp.getString(prefKey, null);
        if (cached != null) return cached;        // fast path ✔

        KeyStore ks = KeyStore.getInstance(PROVIDER);
        ks.load(null);                            // may throw IOException
        if (ks.containsAlias(alias)) {            // key exists but cache was wiped
            PublicKey pk = ks.getCertificate(alias).getPublicKey();
            cached = Base64.encodeToString(pk.getEncoded(), Base64.NO_WRAP);
            sp.edit().putString(prefKey, cached).apply();
            return cached;
        }

        /* ─── generate new pair ─── */
        KeyPair kp;
        try {                                     // modern devices
            kp = generateKeyPair(alias, ALG_X25519, null);
        } catch (Exception unsupported) {         // fallback for older API
            kp = generateKeyPair(alias, ALG_EC, new ECGenParameterSpec(CURVE_FALLBACK));
        }

        cached = Base64.encodeToString(kp.getPublic().getEncoded(), Base64.NO_WRAP);
        sp.edit().putString(prefKey, cached).apply();
        return cached;
    }

    /** Computes a 32‑byte shared secret with the peer’s Base64‑encoded public key */
    public static byte[] deriveSharedKey(String theirPub64) throws Exception {

        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) throw new IllegalStateException("User not signed‑in");
        String alias = aliasForUid(uid);

        KeyStore ks = KeyStore.getInstance(PROVIDER);
        ks.load(null);                            // may throw IOException
        PrivateKey myPriv = (PrivateKey) ks.getKey(alias, null);

        /* Choose algorithm family based on our private key */
        String privAlg   = myPriv.getAlgorithm();               // "X25519" or "EC"
        String agreeAlg  = privAlg.equals(ALG_EC) ? "ECDH" : "X25519";
        String kfAlg     = privAlg.equals(ALG_EC) ? ALG_EC  : "XDH";

        KeyFactory kf = KeyFactory.getInstance(kfAlg);
        PublicKey  theirPub = kf.generatePublic(
                new X509EncodedKeySpec(Base64.decode(theirPub64, Base64.NO_WRAP)));

        KeyAgreement ka = KeyAgreement.getInstance(agreeAlg);
        ka.init(myPriv);
        ka.doPhase(theirPub, true);
        byte[] secret = ka.generateSecret();                    // ≥32 bytes on P‑256
        return secret.length == 32 ? secret : trimTo32(secret);
    }

    /* ─────────────────────  INTERNAL  ───────────────────── */

    private static String aliasForUid(String uid) {
        return "SSK_" + uid;            // separate keystore entry per account
    }

    private static KeyPair generateKeyPair(String alias,
                                           String alg,
                                           ECGenParameterSpec spec) throws Exception {

        KeyPairGenerator kpg = KeyPairGenerator.getInstance(alg, PROVIDER);
        KeyGenParameterSpec.Builder b = new KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_AGREE_KEY)
                .setDigests(KeyProperties.DIGEST_NONE);

        if (spec != null) b.setAlgorithmParameterSpec(spec);
        kpg.initialize(b.build());
        return kpg.generateKeyPair();
    }

    /** Some EC curves give 33 bytes – cut / pad to 32 so AES‑256 key‑len is consistent  */
    private static byte[] trimTo32(byte[] in) {
        if (in.length == 32) return in;
        byte[] out = new byte[32];
        System.arraycopy(in, in.length - 32, out, 0, 32);
        return out;
    }
}
