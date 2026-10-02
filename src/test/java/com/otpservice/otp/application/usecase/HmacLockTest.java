package com.otpservice.otp.application.usecase;

import com.otpservice.otp.domain.exception.InvalidOtpException;
import com.otpservice.otp.domain.exception.OtpBlockedException;
import com.otpservice.otp.domain.model.HmacCredential;
import com.otpservice.otp.domain.valueobject.EmailAddress;
import com.otpservice.otp.domain.valueobject.HmacType;
import com.otpservice.otp.domain.valueobject.OtpProtocol;
import com.otpservice.otp.domain.valueobject.Purpose;
import com.otpservice.otp.support.ProtocolStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.otpservice.otp.support.ProtocolStack.LOCK_SECONDS;
import static com.otpservice.otp.support.ProtocolStack.wrong;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// El bloqueo de HOTP y TOTP no se levanta pidiendo otro codigo: solo vence con otp.lock-seconds.
class HmacLockTest {

    private static final EmailAddress ANA = new EmailAddress("ana@gmail.com");

    private ProtocolStack stack;

    @BeforeEach
    void setUp() {
        stack = new ProtocolStack();
    }

    private String send(OtpProtocol protocol) {
        return stack.send(ANA, protocol, Purpose.LOGIN, 30).demoCode();
    }

    private void check(OtpProtocol protocol, String code) {
        stack.check(ANA, protocol, Purpose.LOGIN, code);
    }

    private HmacCredential credential(OtpProtocol protocol) {
        return stack.credentials.find(ANA.getValue(), HmacType.valueOf(protocol.name()), Purpose.LOGIN).orElseThrow();
    }

    private void failThreeTimes(OtpProtocol protocol, String code) {
        assertThatThrownBy(() -> check(protocol, wrong(code))).hasMessageContaining("intento 1 de 3");
        assertThatThrownBy(() -> check(protocol, wrong(code))).hasMessageContaining("intento 2 de 3");
        assertThatThrownBy(() -> check(protocol, wrong(code))).isInstanceOf(OtpBlockedException.class);
    }

    @ParameterizedTest(name = "{0}: tres fallos bloquean y pedir otro codigo no desbloquea")
    @EnumSource(value = OtpProtocol.class, names = {"HOTP", "TOTP"})
    void pedirOtroCodigoNoLevantaElBloqueo(OtpProtocol protocol) {
        String code = send(protocol);
        failThreeTimes(protocol, code);
        HmacCredential locked = credential(protocol);

        assertThatThrownBy(() -> send(protocol)).isInstanceOf(OtpBlockedException.class);
        assertThatThrownBy(() -> send(protocol)).isInstanceOf(OtpBlockedException.class);
        assertThatThrownBy(() -> check(protocol, code)).isInstanceOf(OtpBlockedException.class);

        HmacCredential after = credential(protocol);
        assertThat(after.getLockedUntil()).isEqualTo(locked.getLockedUntil());
        assertThat(after.getIssuedCounter()).isEqualTo(locked.getIssuedCounter());
    }

    @ParameterizedTest(name = "{0}: el bloqueo dura lock-seconds y no la retencion")
    @EnumSource(value = OtpProtocol.class, names = {"HOTP", "TOTP"})
    void elBloqueoDuraLockSeconds(OtpProtocol protocol) {
        failThreeTimes(protocol, send(protocol));
        assertThat(credential(protocol).getLockedUntil()).isEqualTo(stack.clock.instant().plusSeconds(LOCK_SECONDS));

        stack.clock.plusSeconds(LOCK_SECONDS - 1);
        assertThatThrownBy(() -> send(protocol)).isInstanceOf(OtpBlockedException.class);

        stack.clock.plusSeconds(1);
        String fresh = send(protocol);
        assertThat(stack.check(ANA, protocol, Purpose.LOGIN, fresh).success()).isTrue();
    }

    @ParameterizedTest(name = "{0}: emitir un codigo no reinicia los fallos")
    @EnumSource(value = OtpProtocol.class, names = {"HOTP", "TOTP"})
    void emitirNoReiniciaLosFallos(OtpProtocol protocol) {
        String code = send(protocol);
        assertThatThrownBy(() -> check(protocol, wrong(code))).hasMessageContaining("intento 1 de 3");
        assertThatThrownBy(() -> check(protocol, wrong(code))).hasMessageContaining("intento 2 de 3");

        String next = send(protocol);
        assertThat(credential(protocol).getFailedAttempts()).isEqualTo(2);
        assertThatThrownBy(() -> check(protocol, wrong(next))).isInstanceOf(OtpBlockedException.class);
    }

    @ParameterizedTest(name = "{0}: una verificacion correcta reinicia los fallos")
    @EnumSource(value = OtpProtocol.class, names = {"HOTP", "TOTP"})
    void unAciertoReiniciaLosFallos(OtpProtocol protocol) {
        String code = send(protocol);
        assertThatThrownBy(() -> check(protocol, wrong(code))).isInstanceOf(InvalidOtpException.class);
        assertThatThrownBy(() -> check(protocol, wrong(code))).isInstanceOf(InvalidOtpException.class);

        check(protocol, code);

        assertThat(credential(protocol).getFailedAttempts()).isZero();
    }

    @ParameterizedTest(name = "{0}: el bloqueo de un proposito no bloquea otro")
    @EnumSource(value = OtpProtocol.class, names = {"HOTP", "TOTP"})
    void elBloqueoEsPorProposito(OtpProtocol protocol) {
        failThreeTimes(protocol, send(protocol));

        String payment = stack.send(ANA, protocol, Purpose.PAYMENT_CONFIRMATION, 30).demoCode();
        assertThat(stack.check(ANA, protocol, Purpose.PAYMENT_CONFIRMATION, payment).success()).isTrue();
    }
}
