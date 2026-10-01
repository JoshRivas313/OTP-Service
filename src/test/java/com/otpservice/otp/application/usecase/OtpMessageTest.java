package com.otpservice.otp.application.usecase;

import com.otpservice.otp.application.protocol.IssuedCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OtpMessageTest {

    private static final String TEMPLATE = "Tu código de verificación es %s. Vence en %d segundos.";
    private static final IssuedCode WITH_EXPIRY = new IssuedCode("482913", 60L, null, null);
    private static final IssuedCode WITHOUT_EXPIRY = new IssuedCode("482913", null, 0L, null);

    @Test
    void defaultMessageStatesWhenTheCodeExpires() {
        assertThat(OtpMessage.build(null, true, WITH_EXPIRY, TEMPLATE))
                .isEqualTo("Tu código de verificación es 482913. Vence en 60 segundos.");
    }

    @Test
    void defaultMessageOfACodeWithoutExpiryStatesItLastsUntilUsed() {
        assertThat(OtpMessage.build(null, false, WITHOUT_EXPIRY, TEMPLATE))
                .isEqualTo("Tu código de verificación es 482913. Sirve hasta que lo uses.");
    }

    @Test
    void blankCustomMessageFallsBackToTheDefault() {
        assertThat(OtpMessage.build("   ", true, WITH_EXPIRY, TEMPLATE))
                .isEqualTo("Tu código de verificación es 482913. Vence en 60 segundos.");
    }

    @Test
    void customMessageReplacesTheCodeAndTheSeconds() {
        assertThat(OtpMessage.build("Clave {code}, caduca en {seconds}s", true, WITH_EXPIRY, TEMPLATE))
                .isEqualTo("Clave 482913, caduca en 60s");
    }

    @Test
    void customMessageOfACodeWithoutExpiryDropsTheExpiryClause() {
        assertThat(OtpMessage.build("Tu código es {code}. Vence en {seconds} segundos.", false, WITHOUT_EXPIRY, TEMPLATE))
                .isEqualTo("Tu código es 482913. Sirve hasta que lo uses.");
    }
}
