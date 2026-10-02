package com.otpservice.otp.application.usecase;

import com.otpservice.otp.application.exception.OtpNotFoundException;
import com.otpservice.otp.domain.model.HmacCredential;
import com.otpservice.otp.domain.valueobject.EmailAddress;
import com.otpservice.otp.domain.valueobject.HmacType;
import com.otpservice.otp.domain.valueobject.OtpProtocol;
import com.otpservice.otp.domain.valueobject.Purpose;
import com.otpservice.otp.support.ProtocolStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Un codigo pedido para un proposito solo sirve para ese proposito, con cualquiera de los tres protocolos.
class PurposeTest {

    private static final EmailAddress ANA = new EmailAddress("ana@gmail.com");

    private ProtocolStack stack;

    @BeforeEach
    void setUp() {
        stack = new ProtocolStack();
    }

    private String send(OtpProtocol protocol, Purpose purpose) {
        return stack.send(ANA, protocol, purpose, 30).demoCode();
    }

    @ParameterizedTest(name = "{0}: LOGIN se verifica como LOGIN")
    @EnumSource(OtpProtocol.class)
    void loginSeVerificaComoLogin(OtpProtocol protocol) {
        String code = send(protocol, Purpose.LOGIN);

        assertThat(stack.check(ANA, protocol, Purpose.LOGIN, code).success()).isTrue();
    }

    @ParameterizedTest(name = "{0}: un codigo de LOGIN no confirma un pago")
    @EnumSource(OtpProtocol.class)
    void unCodigoDeLoginNoConfirmaUnPago(OtpProtocol protocol) {
        String code = send(protocol, Purpose.LOGIN);

        assertThatThrownBy(() -> stack.check(ANA, protocol, Purpose.PAYMENT_CONFIRMATION, code))
                .isInstanceOf(OtpNotFoundException.class);
        assertThat(stack.check(ANA, protocol, Purpose.LOGIN, code).success()).isTrue();
    }

    @ParameterizedTest(name = "{0}: un codigo de recuperacion no inicia sesion")
    @EnumSource(OtpProtocol.class)
    void unCodigoDeRecuperacionNoIniciaSesion(OtpProtocol protocol) {
        String code = send(protocol, Purpose.PASSWORD_RECOVERY);

        assertThatThrownBy(() -> stack.check(ANA, protocol, Purpose.LOGIN, code))
                .isInstanceOf(OtpNotFoundException.class);
    }

    @ParameterizedTest(name = "{0}: el mismo correo con dos propositos tiene contextos independientes")
    @EnumSource(OtpProtocol.class)
    void dosPropositosSonContextosIndependientes(OtpProtocol protocol) {
        String login = send(protocol, Purpose.LOGIN);
        String register = send(protocol, Purpose.REGISTER);

        assertThat(stack.check(ANA, protocol, Purpose.REGISTER, register).success()).isTrue();
        assertThat(stack.check(ANA, protocol, Purpose.LOGIN, login).success()).isTrue();
    }

    @Test
    void pedirUnOtpParaOtroPropositoNoInvalidaElAnterior() {
        String login = send(OtpProtocol.OTP, Purpose.LOGIN);
        send(OtpProtocol.OTP, Purpose.PAYMENT_CONFIRMATION);

        assertThat(stack.check(ANA, OtpProtocol.OTP, Purpose.LOGIN, login).success()).isTrue();
    }

    @ParameterizedTest(name = "{0}: una credencial y un secreto por proposito")
    @EnumSource(value = HmacType.class)
    void hotpYTotpUsanUnaCredencialPorProposito(HmacType type) {
        OtpProtocol protocol = OtpProtocol.valueOf(type.name());
        send(protocol, Purpose.LOGIN);
        send(protocol, Purpose.REGISTER);

        HmacCredential login = stack.credentials.find(ANA.getValue(), type, Purpose.LOGIN).orElseThrow();
        HmacCredential register = stack.credentials.find(ANA.getValue(), type, Purpose.REGISTER).orElseThrow();
        assertThat(login.getId()).isNotEqualTo(register.getId());
        assertThat(login.getSecret().ciphertext()).isNotEqualTo(register.getSecret().ciphertext());
        assertThat(login.secretContext()).isNotEqualTo(register.secretContext());
    }

    @Test
    void sinPropositoEsLogin() {
        assertThat(Purpose.from(null)).isEqualTo(Purpose.LOGIN);
        assertThat(Purpose.from(" payment_confirmation ")).isEqualTo(Purpose.PAYMENT_CONFIRMATION);
    }
}
