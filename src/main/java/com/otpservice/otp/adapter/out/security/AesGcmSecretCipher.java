package com.otpservice.otp.adapter.out.security;

import com.otpservice.otp.adapter.config.HmacProperties;
import com.otpservice.otp.application.port.out.SecretCipherPort;
import com.otpservice.otp.domain.valueobject.HmacSecret;
import com.otpservice.otp.domain.valueobject.EncryptedSecret;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

// Se cifra y no se hashea: hay que recuperar el secreto para recalcular el codigo.
@Slf4j
@Component
@DependsOn("requiredSecretsGuard")
public class AesGcmSecretCipher implements SecretCipherPort {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public AesGcmSecretCipher(HmacProperties properties) {
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(properties.encryptionKey());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("hmac.encryption-key debe estar en Base64", exception);
        }
        if (raw.length != KEY_BYTES) {
            throw new IllegalStateException("hmac.encryption-key debe tener 32 bytes (AES-256); tiene " + raw.length);
        }
        if (properties.usingInsecureDevKey()) {
            log.warn("hmac.encryption-key usa el valor de desarrollo por defecto");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    @Override
    public EncryptedSecret encrypt(HmacSecret secret, String context) {
        byte[] nonce = new byte[NONCE_BYTES];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = init(Cipher.ENCRYPT_MODE, nonce, context);
            return new EncryptedSecret(cipher.doFinal(secret.bytes()), nonce);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo cifrar el secreto", exception);
        }
    }

    @Override
    public HmacSecret decrypt(EncryptedSecret secret, String context) {
        try {
            Cipher cipher = init(Cipher.DECRYPT_MODE, secret.nonce(), context);
            return new HmacSecret(cipher.doFinal(secret.ciphertext()));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo descifrar el secreto: clave distinta o datos alterados", exception);
        }
    }

    private Cipher init(int mode, byte[] nonce, String context) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
