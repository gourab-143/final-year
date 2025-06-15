package com.example.securesamvad.crypto;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Date;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** stateless AES‑256/GCM helpers */
public final  class CryptoAES {

    private CryptoAES(){}

    /* holder */
    public static class Bundle {
        public final String cipher64, iv64;
        public Bundle(String c,String i){cipher64=c;iv64=i;}
    }

    /** encrypts plain → Bundle(cipher, iv) */
    public static Bundle encrypt(String plain, byte[] key)
            throws GeneralSecurityException {

        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);

        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        SecretKey sk = new SecretKeySpec(key, "AES");
        c.init(Cipher.ENCRYPT_MODE, sk, new GCMParameterSpec(128, iv));

        byte[] cipher = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
        return new Bundle(
                Base64.encodeToString(cipher, Base64.NO_WRAP),
                Base64.encodeToString(iv,     Base64.NO_WRAP));
    }

    /** decrypts Base64(cipher), Base64(iv) → plaintext */
    public static String decrypt(String cipher64,String iv64, byte[] key)
            throws GeneralSecurityException {

        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        SecretKey sk = new SecretKeySpec(key, "AES");
        c.init(Cipher.DECRYPT_MODE, sk,
                new GCMParameterSpec(128, Base64.decode(iv64, Base64.NO_WRAP)));

        byte[] plain = c.doFinal(Base64.decode(cipher64, Base64.NO_WRAP));
        return new String(plain, StandardCharsets.UTF_8);
    }
}
