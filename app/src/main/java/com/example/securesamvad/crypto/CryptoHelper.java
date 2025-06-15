package com.example.securesamvad.crypto;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.*;
import android.util.Base64;

import java.security.*;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.KeyAgreement;

/**
 * Generates a long‑term keypair in AndroidKeystore.
 *  • Tries X25519 (API 31+).
 *  • Fallback: P‑256 ECDH (works on Android 5‑11).
 * Derives a 32‑byte shared secret for AES.
 */
public  class CryptoHelper {

    private static final String PREF_NAME = "crypto_pref";
    private static final String PREF_PUB  = "PUB_KEY";
    private static final String ALIAS     = "SecureSamvadKey";

    private static final String ALG_X25519 = "X25519";
    private static final String ALG_EC     = "EC";               // P‑256
    private static final String CURVE_FALLBACK = "secp256r1";

    private static final String KSTORE = "AndroidKeyStore";

    private CryptoHelper() {}

    /* ---------- get or create public key (Base64) ---------- */
    public static String getMyPublicKey(Context ctx) throws Exception {

        SharedPreferences sp = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String pub64 = sp.getString(PREF_PUB, null);
        if (pub64 != null) return pub64;                // already generated

        /* try modern X25519, otherwise EC P‑256 */
        KeyPair kp;
        try {
            kp = generateKeyPair(ALG_X25519, null);     // API 31 +
        } catch (Exception e) {                         // fallback
            kp = generateKeyPair(ALG_EC, new ECGenParameterSpec(CURVE_FALLBACK));
        }

        pub64 = Base64.encodeToString(kp.getPublic().getEncoded(), Base64.NO_WRAP);
        sp.edit().putString(PREF_PUB, pub64).apply();
        return pub64;
    }

    /* ---------- derive 32‑byte shared secret ---------- */
    public static byte[] deriveSharedKey(String theirPub64) throws Exception {

        KeyStore ks = KeyStore.getInstance(KSTORE);
        ks.load(null);
        PrivateKey myPriv = (PrivateKey) ks.getKey(ALIAS, null);

        String myAlg = myPriv.getAlgorithm();           // "X25519" or "EC"

        /* decode peer public key */
        byte[] other = Base64.decode(theirPub64, Base64.NO_WRAP);
        KeyFactory kf = KeyFactory.getInstance(myAlg);
        PublicKey theirPub = kf.generatePublic(new X509EncodedKeySpec(other));

        /* -------- FIX: use "ECDH" when algorithm is EC -------- */
        String kaName = myAlg.equals(ALG_EC) ? "ECDH" : myAlg;   // ✔
        KeyAgreement ka = KeyAgreement.getInstance(kaName);
        /* ------------------------------------------------------ */

        ka.init(myPriv);
        ka.doPhase(theirPub, true);
        byte[] secret = ka.generateSecret();             // 32 or 33 bytes
        return secret.length == 32 ? secret : trimTo32(secret);
    }

    /* ---------- helpers ---------- */
    private static KeyPair generateKeyPair(String alg, AlgorithmParameterSpec spec) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(alg, KSTORE);
        KeyGenParameterSpec.Builder b =
                new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_AGREE_KEY)
                        .setDigests(KeyProperties.DIGEST_NONE);
        if (spec != null) b.setAlgorithmParameterSpec(spec);
        kpg.initialize(b.build());
        return kpg.generateKeyPair();
    }

    private static byte[] trimTo32(byte[] in) {
        byte[] out = new byte[32];
        System.arraycopy(in, in.length - 32, out, 0, 32);
        return out;
    }
}
