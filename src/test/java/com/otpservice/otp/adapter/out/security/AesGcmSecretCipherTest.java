package com.otpservice.otp.adapter.out.security;

import com.otpservice.otp.adapter.config.AuthenticatorProperties;
import com.otpservice.otp.domain.valueobject.AuthenticatorSecret;
import com.otpservice.otp.domain.valueobject.EncryptedSecret;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmSecretCipherTest {

    private static AuthenticatorProperties withKey(String key) {
        return new AuthenticatorProperties("OTP Service", 1, 10, 5, 600, 600, key);
    }

    private final AesGcmSecretCipher cipher = new AesGcmSecretCipher(withKey(AuthenticatorProperties.INSECURE_DEV_KEY));
    private final AuthenticatorSecret secret = AuthenticatorSecret.generate();

    @Test
    void descifraLoQueCifro() {
        EncryptedSecret encrypted = cipher.encrypt(secret, "ana@gmail.com|TOTP");

        assertThat(cipher.decrypt(encrypted, "ana@gmail.com|TOTP")).isEqualTo(secret);
    }

    @Test
    void elTextoCifradoNoContieneElSecreto() {
        EncryptedSecret encrypted = cipher.encrypt(secret, "ana@gmail.com|TOTP");

        assertThat(encrypted.ciphertext()).isNotEqualTo(secret.bytes());
    }

    @Test
    void cifrarDosVecesDaResultadosDistintos() {
        EncryptedSecret first = cipher.encrypt(secret, "ana@gmail.com|TOTP");
        EncryptedSecret second = cipher.encrypt(secret, "ana@gmail.com|TOTP");

        assertThat(first.nonce()).isNotEqualTo(second.nonce());
        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
    }

    @Test
    void unSecretoMovidoAOtroRegistroNoSeDescifra() {
        EncryptedSecret encrypted = cipher.encrypt(secret, "ana@gmail.com|TOTP");

        assertThatThrownBy(() -> cipher.decrypt(encrypted, "intruso@gmail.com|TOTP"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void detectaDatosAlterados() {
        EncryptedSecret encrypted = cipher.encrypt(secret, "ana@gmail.com|TOTP");
        byte[] tampered = encrypted.ciphertext();
        tampered[0] ^= 1;

        assertThatThrownBy(() -> cipher.decrypt(new EncryptedSecret(tampered, encrypted.nonce()), "ana@gmail.com|TOTP"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void otraClaveNoPuedeDescifrar() {
        byte[] otherKey = new byte[32];
        Arrays.fill(otherKey, (byte) 7);
        AesGcmSecretCipher other = new AesGcmSecretCipher(withKey(java.util.Base64.getEncoder().encodeToString(otherKey)));
        EncryptedSecret encrypted = cipher.encrypt(secret, "ana@gmail.com|TOTP");

        assertThatThrownBy(() -> other.decrypt(encrypted, "ana@gmail.com|TOTP")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rechazaClavesQueNoSonDe32Bytes() {
        assertThatThrownBy(() -> new AesGcmSecretCipher(withKey("c2hvcnQ=")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }
}
