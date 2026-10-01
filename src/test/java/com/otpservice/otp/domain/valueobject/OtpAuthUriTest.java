package com.otpservice.otp.domain.valueobject;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OtpAuthUriTest {

    private static final AuthenticatorSecret SECRET =
            new AuthenticatorSecret("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @Test
    void totpLlevaElPeriodoYLaEtiquetaCodificada() {
        String uri = OtpAuthUri.totp("OTP Service", "ana@gmail.com", SECRET, 6, 30);

        assertThat(uri).isEqualTo("otpauth://totp/OTP%20Service:ana%40gmail.com"
                + "?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
                + "&issuer=OTP%20Service&algorithm=SHA1&digits=6&period=30");
    }

    @Test
    void hotpLlevaElContadorInicial() {
        String uri = OtpAuthUri.hotp("OTP Service", "ana@gmail.com", SECRET, 8, 0);

        assertThat(uri).startsWith("otpauth://hotp/").endsWith("&digits=8&counter=0");
    }

    @Test
    void elSecretoNoAparecePorToString() {
        assertThat(SECRET.toString()).doesNotContain(SECRET.base32());
    }
}
