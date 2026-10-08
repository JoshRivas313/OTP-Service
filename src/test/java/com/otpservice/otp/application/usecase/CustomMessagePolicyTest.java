package com.otpservice.otp.application.usecase;

import com.otpservice.otp.domain.exception.InvalidCodeRequestException;
import com.otpservice.otp.domain.model.Otp;
import com.otpservice.otp.domain.valueobject.EmailAddress;
import com.otpservice.otp.domain.valueobject.OtpProtocol;
import com.otpservice.otp.domain.valueobject.Purpose;
import com.otpservice.otp.support.ProtocolStack;
import com.otpservice.otp.support.TestOtpProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Quien pide el codigo no puede escribir el texto que sale hacia un tercero, salvo que la configuracion lo permita.
class CustomMessagePolicyTest {

    private static final EmailAddress ANA = new EmailAddress("ana@gmail.com");
    private static final String PHISHING = "Aviso: confirma en http://sitio-falso.test/login. Código {code}";

    private static ProtocolStack publicServer() {
        return new ProtocolStack(TestOtpProperties.otp().demoMode(false).customMessageEnabled(false).build());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(OtpProtocol.class)
    void aServerThatDoesNotAllowItRejectsTheCustomMessage(OtpProtocol protocol) {
        ProtocolStack stack = publicServer();

        assertThatThrownBy(() -> stack.sendWithMessage(ANA, protocol, Purpose.LOGIN, PHISHING))
                .isInstanceOf(InvalidCodeRequestException.class)
                .hasMessageContaining("mensaje personalizado");
        assertThat(stack.lastMessage()).isNull();
    }

    @Test
    void aRejectedMessageDoesNotChangeTheState() {
        ProtocolStack stack = publicServer();
        stack.send(ANA, OtpProtocol.OTP, Purpose.LOGIN, 30);
        Otp before = stack.otpStore.findLatest(ANA.getValue(), Purpose.LOGIN).orElseThrow();

        assertThatThrownBy(() -> stack.sendWithMessage(ANA, OtpProtocol.OTP, Purpose.LOGIN, PHISHING))
                .isInstanceOf(InvalidCodeRequestException.class);

        // El codigo anterior sigue siendo el vigente: el rechazo ocurre antes de emitir.
        Otp after = stack.otpStore.findLatest(ANA.getValue(), Purpose.LOGIN).orElseThrow();
        assertThat(after.getId()).isEqualTo(before.getId());
        assertThat(after.isInvalidated()).isFalse();
    }

    @Test
    void withoutACustomMessageTheServerWritesTheTextFromThePurpose() {
        ProtocolStack stack = publicServer();

        String code = stack.send(ANA, OtpProtocol.HOTP, Purpose.PAYMENT_CONFIRMATION, null).demoCode();

        // En modo publico no hay demoCode; el texto sale por el canal y lo recibe el destinatario.
        assertThat(code).isNull();
        assertThat(stack.lastMessage())
                .startsWith("Tu código para confirmar tu pago es ")
                .endsWith("Sirve hasta que lo uses.")
                .doesNotContain("sitio-falso");
    }

    @Test
    void blankMessageCountsAsNoMessage() {
        ProtocolStack stack = publicServer();

        stack.sendWithMessage(ANA, OtpProtocol.OTP, Purpose.LOGIN, "   ");

        assertThat(stack.lastMessage()).startsWith("Tu código para iniciar sesión es ");
    }

    @Test
    void whenTheConfigurationAllowsItTheCustomMessageIsSent() {
        ProtocolStack stack = new ProtocolStack(
                TestOtpProperties.otp().demoMode(false).customMessageEnabled(true).build());

        stack.sendWithMessage(ANA, OtpProtocol.OTP, Purpose.LOGIN, "Clave {code}");

        assertThat(stack.lastMessage()).startsWith("Clave ").doesNotContain("{code}");
    }

    @Test
    void demoModeAllowsItBecauseNothingIsDelivered() {
        ProtocolStack stack = new ProtocolStack(
                TestOtpProperties.otp().demoMode(true).customMessageEnabled(false).build());

        stack.sendWithMessage(ANA, OtpProtocol.OTP, Purpose.LOGIN, "Clave {code}");

        assertThat(stack.lastMessage()).startsWith("Clave ");
    }
}
