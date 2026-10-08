package com.otpservice.otp.application.usecase;

import com.otpservice.otp.application.protocol.IssuedCode;
import com.otpservice.otp.domain.valueobject.Purpose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class OtpMessageTest {

    private static final IssuedCode WITH_EXPIRY = new IssuedCode("482913", 60L, null, null);
    private static final IssuedCode WITHOUT_EXPIRY = new IssuedCode("482913", null, 0L, null);
    private static final IssuedCode TIME_BASED = new IssuedCode("482913", 43L, null, 59697031L);

    // El servidor decide el texto a partir del proposito: el cliente no lo escribe.
    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "LOGIN, para iniciar sesión",
            "REGISTER, para crear tu cuenta",
            "PASSWORD_RECOVERY, para recuperar tu acceso",
            "PAYMENT_CONFIRMATION, para confirmar tu pago"
    })
    void defaultMessageStatesThePurposeAndWhenTheCodeExpires(Purpose purpose, String reason) {
        assertThat(OtpMessage.build(null, true, WITH_EXPIRY, purpose))
                .isEqualTo("Tu código " + reason + " es 482913. Vence en 60 segundos.");
    }

    @Test
    void defaultMessageOfACodeWithoutExpiryStatesItLastsUntilUsed() {
        assertThat(OtpMessage.build(null, false, WITHOUT_EXPIRY, Purpose.LOGIN))
                .isEqualTo("Tu código para iniciar sesión es 482913. Sirve hasta que lo uses.");
    }

    @Test
    void defaultMessageOfATimeBasedCodeSaysItExpiresWhenItsWindowCloses() {
        assertThat(OtpMessage.build(null, true, TIME_BASED, Purpose.LOGIN))
                .isEqualTo("Tu código para iniciar sesión es 482913. Vence en 43 segundos, al cerrar su ventana.");
    }

    @Test
    void blankCustomMessageFallsBackToTheDefault() {
        assertThat(OtpMessage.build("   ", true, WITH_EXPIRY, Purpose.PAYMENT_CONFIRMATION))
                .isEqualTo("Tu código para confirmar tu pago es 482913. Vence en 60 segundos.");
    }

    @Test
    void customMessageReplacesTheCodeAndTheSeconds() {
        assertThat(OtpMessage.build("Clave {code}, caduca en {seconds}s", true, WITH_EXPIRY, Purpose.LOGIN))
                .isEqualTo("Clave 482913, caduca en 60s");
    }

    @Test
    void customMessageOfACodeWithoutExpiryDropsTheExpiryClause() {
        assertThat(OtpMessage.build("Tu código es {code}. Vence en {seconds} segundos.", false, WITHOUT_EXPIRY,
                Purpose.LOGIN))
                .isEqualTo("Tu código es 482913. Sirve hasta que lo uses.");
    }
}
