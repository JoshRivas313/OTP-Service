package com.otpservice.otp.application.usecase;

import com.otpservice.otp.application.dto.GenerateOtpResult;
import com.otpservice.otp.application.dto.VerifyOtpResult;
import com.otpservice.otp.application.exception.OtpNotFoundException;
import com.otpservice.otp.domain.exception.InvalidCodeRequestException;
import com.otpservice.otp.domain.exception.InvalidOtpException;
import com.otpservice.otp.domain.exception.OtpAlreadyUsedException;
import com.otpservice.otp.domain.exception.OtpExpiredException;
import com.otpservice.otp.domain.valueobject.EmailAddress;
import com.otpservice.otp.domain.valueobject.OtpProtocol;
import com.otpservice.otp.domain.valueobject.Purpose;
import com.otpservice.otp.support.ProtocolStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.otpservice.otp.support.ProtocolStack.wrong;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveredProtocolsTest {

    private static final EmailAddress ANA = new EmailAddress("ana@gmail.com");

    private ProtocolStack stack;

    @BeforeEach
    void setUp() {
        stack = new ProtocolStack();
    }

    private GenerateOtpResult send(OtpProtocol protocol, Integer duration) {
        return stack.send(ANA, protocol, Purpose.LOGIN, duration);
    }

    private VerifyOtpResult check(OtpProtocol protocol, String code) {
        return stack.check(ANA, protocol, Purpose.LOGIN, code);
    }

    @Nested
    class Aleatorio {

        @Test
        void sigueFuncionandoComoAntesYGuardaElCodigo() {
            GenerateOtpResult sent = send(OtpProtocol.OTP, 30);

            assertThat(sent.expiresInSeconds()).isEqualTo(30);
            assertThat(stack.otpStore.findLatest(ANA.getValue(), Purpose.LOGIN)).isPresent();
            assertThat(check(OtpProtocol.OTP, sent.demoCode()).success()).isTrue();
        }

        @Test
        void sinProtocoloEsOtp() {
            GenerateOtpResult sent = send(null, 30);

            assertThat(sent.protocol()).isEqualTo(OtpProtocol.OTP);
        }
    }

    @Nested
    class Hotp {

        @Test
        void noGuardaElCodigoEnviado() {
            send(OtpProtocol.HOTP, null);

            assertThat(stack.otpStore.findLatest(ANA.getValue(), Purpose.LOGIN)).isEmpty();
        }

        @Test
        void cadaEnvioAvanzaElContadorYElMensajeNoPrometeSegundos() {
            GenerateOtpResult first = send(OtpProtocol.HOTP, null);
            GenerateOtpResult second = send(OtpProtocol.HOTP, null);

            assertThat(second.counter()).isEqualTo(first.counter() + 1);
            assertThat(first.expiresInSeconds()).isNull();
            assertThat(stack.lastMessage()).contains("Sirve hasta que lo uses").doesNotContain("segundos");
        }

        @Test
        void noCaducaPorTiempo() {
            String code = send(OtpProtocol.HOTP, null).demoCode();
            stack.clock.plusSeconds(7 * 24 * 3600);

            assertThat(check(OtpProtocol.HOTP, code).success()).isTrue();
        }

        @Test
        void losCodigosPendientesSiguenValiendoHastaQueSeUseUnoPosterior() {
            String first = send(OtpProtocol.HOTP, null).demoCode();
            String second = send(OtpProtocol.HOTP, null).demoCode();
            String third = send(OtpProtocol.HOTP, null).demoCode();

            assertThat(check(OtpProtocol.HOTP, second).counter()).isEqualTo(1);
            assertThatThrownBy(() -> check(OtpProtocol.HOTP, first)).isInstanceOf(OtpAlreadyUsedException.class);
            assertThat(check(OtpProtocol.HOTP, third).counter()).isEqualTo(2);
        }

        @Test
        void elMismoCodigoNoSirveDosVeces() {
            String code = send(OtpProtocol.HOTP, null).demoCode();
            check(OtpProtocol.HOTP, code);

            assertThatThrownBy(() -> check(OtpProtocol.HOTP, code)).isInstanceOf(OtpAlreadyUsedException.class);
        }

        @Test
        void soloAceptaEntreSeisYOchoDigitos() {
            assertThatThrownBy(() -> stack.send(ANA, OtpProtocol.HOTP, Purpose.LOGIN, 4, null))
                    .isInstanceOf(InvalidCodeRequestException.class);
        }
    }

    @Nested
    class Totp {

        @Test
        void noGuardaElCodigoEnviado() {
            send(OtpProtocol.TOTP, 30);

            assertThat(stack.otpStore.findLatest(ANA.getValue(), Purpose.LOGIN)).isEmpty();
        }

        @Test
        void enLaMismaVentanaLlegaElMismoCodigo() {
            GenerateOtpResult first = send(OtpProtocol.TOTP, 30);
            stack.clock.plusSeconds(5);
            GenerateOtpResult second = send(OtpProtocol.TOTP, 30);

            assertThat(second.demoCode()).isEqualTo(first.demoCode());
            assertThat(second.timeStep()).isEqualTo(first.timeStep());
        }

        @Test
        void venceAlTerminarSuVentanaMasLaTolerancia() {
            GenerateOtpResult sent = send(OtpProtocol.TOTP, 30);
            assertThat(sent.expiresInSeconds()).isEqualTo(50);

            stack.clock.plusSeconds(sent.expiresInSeconds() + 1);
            assertThatThrownBy(() -> check(OtpProtocol.TOTP, sent.demoCode())).isInstanceOf(OtpExpiredException.class);
        }

        @Test
        void unCodigoVencidoNoGastaIntentos() {
            String old = send(OtpProtocol.TOTP, 30).demoCode();
            stack.clock.plusSeconds(120);
            for (int i = 0; i < 5; i++) {
                assertThatThrownBy(() -> check(OtpProtocol.TOTP, old)).isInstanceOf(OtpExpiredException.class);
            }
        }

        @Test
        void elMismoCodigoNoSirveDosVeces() {
            String code = send(OtpProtocol.TOTP, 30).demoCode();
            assertThat(check(OtpProtocol.TOTP, code).timeStep()).isNotNull();

            assertThatThrownBy(() -> check(OtpProtocol.TOTP, code)).isInstanceOf(OtpAlreadyUsedException.class);
        }

        @Test
        void unCodigoIncorrectoGastaUnIntento() {
            String code = send(OtpProtocol.TOTP, 30).demoCode();

            assertThatThrownBy(() -> check(OtpProtocol.TOTP, wrong(code)))
                    .isInstanceOf(InvalidOtpException.class)
                    .hasMessageContaining("intento 1 de 3");
        }
    }

    @Test
    void verificarConOtroProtocoloNoEncuentraElCodigo() {
        send(OtpProtocol.TOTP, 30);

        assertThatThrownBy(() -> check(OtpProtocol.HOTP, "123456")).isInstanceOf(OtpNotFoundException.class);
    }
}
