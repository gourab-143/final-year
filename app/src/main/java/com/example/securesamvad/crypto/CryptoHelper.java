package com.example.securesamvad.crypto;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.*;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

/**
 *  Handles X25519 key‑pair (Android Keystore) + ECDH shared secret.
 */
public  class CryptoHelper {

    private static final String ALIAS    = "SecureSamvadKey";
    private static final String PREF     = "crypto_pref";
    private static final String PREF_PUB = "PUB_KEY";

    private CryptoHelper() {}

    /** returns my Base64 public key, generating pair if first call */
    public static String getMyPublicKey(Context ctx) throws Exception {

        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String pub64 = sp.getString(PREF_PUB, null);
        if (pub64 != null) return pub64;                        // already created

        /* ------ generate new key‑pair in Keystore (X25519) ------ */
        KeyPairGenerator kpg = KeyPairGenerator.getInstance(
                "X25519", "AndroidKeyStore");

        kpg.initialize(new KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_AGREE_KEY)      // ECDH only
                .setDigests(KeyProperties.DIGEST_NONE)        // not used
                .build());

        KeyPair kp = kpg.generateKeyPair();
        byte[] pub = kp.getPublic().getEncoded();             // X.509 format

        pub64 = Base64.encodeToString(pub, Base64.NO_WRAP);
        sp.edit().putString(PREF_PUB, pub64).apply();
        return pub64;
    }

    /** derives 32‑byte shared key with other party’s Base64 public key */
    public static byte[] deriveSharedKey(String theirPub64) throws Exception {

        /* 1. load my private */
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        PrivateKey myPriv = (PrivateKey) ks.getKey(ALIAS, null);

        /* 2. rebuild their public */
        byte[] theirBytes = Base64.decode(theirPub64, Base64.NO_WRAP);
        KeyFactory kf = KeyFactory.getInstance("X25519");
        PublicKey theirPub = kf.generatePublic(new X509EncodedKeySpec(theirBytes));

        /* 3. ECDH */
        KeyAgreement ka = KeyAgreement.getInstance("X25519");
        ka.init(myPriv);
        ka.doPhase(theirPub, true);
        byte[] shared = ka.generateSecret();      // 32 bytes

        return shared;                            // use directly as AES‑256 key
    }
}
