package com.otpservice.otp.application.usecase;

import com.otpservice.otp.application.dto.GenerateOtpResult;
import com.otpservice.otp.application.dto.VerifyOtpResult;
import com.otpservice.otp.domain.exception.InvalidOtpException;
import com.otpservice.otp.domain.exception.OtpAlreadyUsedException;
import com.otpservice.otp.domain.exception.OtpExpiredException;
import com.otpservice.otp.domain.exception.OtpInvalidatedException;
import com.otpservice.otp.domain.model.HmacCredential;
import com.otpservice.otp.domain.service.HmacOtpAlgorithm;
import com.otpservice.otp.domain.valueobject.EmailAddress;
import com.otpservice.otp.domain.valueobject.HmacType;
import com.otpservice.otp.domain.valueobject.OtpProtocol;
import com.otpservice.otp.domain.valueobject.Purpose;
import com.otpservice.otp.support.ProtocolStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// El comportamiento de cada protocolo de punta a punta: lo que el servicio entrega coincide con el algoritmo.
class ProtocolBehaviourTest {

    private static final EmailAddress ANA = new EmailAddress("ana@gmail.com");
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    private ProtocolStack stack;

    @BeforeEach
    void setUp() {
        stack = new ProtocolStack();
    }

    private HmacCredential credential(HmacType type) {
        return stack.credentials.find(ANA.getValue(), type, Purpose.LOGIN).orElseThrow();
    }

    @Nested
    class Aleatorio {

        private GenerateOtpResult send() {
            return stack.send(ANA, OtpProtocol.OTP, Purpose.LOGIN, 30);
        }

        private boolean check(String code) {
            return stack.check(ANA, OtpProtocol.OTP, Purpose.LOGIN, code).success();
        }

        @Test
        void seGeneraYSeVerifica() {
            GenerateOtpResult sent = send();

            assertThat(sent.demoCode()).matches("\\d{6}");
            assertThat(check(sent.demoCode())).isTrue();
        }

        @Test
        void soloSeGuardaElHashDelCodigo() {
            String code = send().demoCode();

            assertThat(stack.otpStore.findLatest(ANA.getValue(), Purpose.LOGIN).orElseThrow().getCodeHash())
                    .isNotEqualTo(code)
                    .doesNotContain(code);
        }

        @Test
        void noSePuedeReutilizar() {
            String code = send().demoCode();
            check(code);

            assertThatThrownBy(() -> check(code)).isInstanceOf(OtpAlreadyUsedException.class);
        }

        @Test
        void venceAlTerminarSuDuracion() {
            String code = send().demoCode();
            stack.clock.plusSeconds(31);

            assertThatThrownBy(() -> check(code)).isInstanceOf(OtpExpiredException.class);
        }

        @Test
        void pedirOtroInvalidaElAnterior() {
            String first = send().demoCode();
            String second = send().demoCode();

            assertThatThrownBy(() -> check(first)).isInstanceOf(InvalidOtpException.class);
            assertThat(check(second)).isTrue();
        }
    }

    @Nested
    class Hotp {

        @Test
        void mismoSecretoYMismoContadorDanElMismoCodigo() {
            assertThat(HmacOtpAlgorithm.hotp(RFC_SECRET, 7, 6)).isEqualTo(HmacOtpAlgorithm.hotp(RFC_SECRET, 7, 6));

            GenerateOtpResult sent = stack.send(ANA, OtpProtocol.HOTP, Purpose.LOGIN, null);
            byte[] secret = stack.secretOf(credential(HmacType.HOTP));
            assertThat(sent.demoCode()).isEqualTo(HmacOtpAlgorithm.hotp(secret, sent.counter(), 6));
        }

        @Test
        void unContadorNuevoDaUnCodigoNuevo() {
            assertThat(HmacOtpAlgorithm.hotp(RFC_SECRET, 0, 6)).isEqualTo("755224");
            assertThat(HmacOtpAlgorithm.hotp(RFC_SECRET, 1, 6)).isEqualTo("287082");

            GenerateOtpResult first = stack.send(ANA, OtpProtocol.HOTP, Purpose.LOGIN, null);
            GenerateOtpResult second = stack.send(ANA, OtpProtocol.HOTP, Purpose.LOGIN, null);
            byte[] secret = stack.secretOf(credential(HmacType.HOTP));

            assertThat(second.counter()).isEqualTo(first.counter() + 1);
            assertThat(second.demoCode()).isEqualTo(HmacOtpAlgorithm.hotp(secret, second.counter(), 6));
            assertThat(credential(HmacType.HOTP).getIssuedCounter()).isEqualTo(2);
        }

        @Test
        void unCodigoConsumidoSeRechaza() {
            String code = stack.send(ANA, OtpProtocol.HOTP, Purpose.LOGIN, null).demoCode();
            stack.check(ANA, OtpProtocol.HOTP, Purpose.LOGIN, code);

            assertThat(credential(HmacType.HOTP).getCounter()).isEqualTo(1);
            assertThatThrownBy(() -> stack.check(ANA, OtpProtocol.HOTP, Purpose.LOGIN, code))
                    .isInstanceOf(OtpAlreadyUsedException.class);
        }
    }

    // Mas pendientes que la ventana de look-ahead (10): se aceptan los 10 emitidos mas recientes.
    @Nested
    class HotpConMuchosPendientes {

        private final List<GenerateOtpResult> sent = new ArrayList<>();

        @BeforeEach
        void emitirDoce() {
            for (int i = 0; i < 12; i++) {
                sent.add(stack.send(ANA, OtpProtocol.HOTP, Purpose.LOGIN, null));
            }
            assertThat(sent.getLast().counter()).isEqualTo(11);
        }

        private VerifyOtpResult check(String code) {
            return stack.check(ANA, OtpProtocol.HOTP, Purpose.LOGIN, code);
        }

        @Test
        void elMasRecienteSeVerifica() {
            assertThat(check(sent.get(11).demoCode()).counter()).isEqualTo(11);
        }

        @Test
        void unPendienteAntiguoDentroDeLaVentanaSeVerifica() {
            assertThat(check(sent.get(2).demoCode()).counter()).isEqualTo(2);
        }

        @Test
        void losQueQuedaronFueraDeLaVentanaSeRechazanSinGastarIntentos() {
            assertThatThrownBy(() -> check(sent.get(0).demoCode())).isInstanceOf(OtpInvalidatedException.class);
            assertThatThrownBy(() -> check(sent.get(1).demoCode())).isInstanceOf(OtpInvalidatedException.class);

            assertThat(credential(HmacType.HOTP).getFailedAttempts()).isZero();
            assertThat(check(sent.get(11).demoCode()).success()).isTrue();
        }

        @Test
        void consumirUnoPosteriorAnulaLosAnteriores() {
            check(sent.get(7).demoCode());

            assertThat(credential(HmacType.HOTP).getCounter()).isEqualTo(8);
            assertThatThrownBy(() -> check(sent.get(5).demoCode())).isInstanceOf(OtpAlreadyUsedException.class);
            assertThat(check(sent.get(9).demoCode()).counter()).isEqualTo(9);
        }

        @Test
        void reutilizarUnContadorConsumidoSeRechaza() {
            check(sent.get(11).demoCode());

            assertThatThrownBy(() -> check(sent.get(11).demoCode())).isInstanceOf(OtpAlreadyUsedException.class);
        }

        @Test
        void unContadorQueNuncaSeEmitioNoSeAcepta() {
            byte[] secret = stack.secretOf(credential(HmacType.HOTP));
            String notIssued = HmacOtpAlgorithm.hotp(secret, 12, 6);

            assertThatThrownBy(() -> check(notIssued)).isInstanceOf(InvalidOtpException.class);
        }
    }

    @Nested
    class Totp {

        @Test
        void enLaMismaVentanaElMismoCodigo() {
            GenerateOtpResult first = stack.send(ANA, OtpProtocol.TOTP, Purpose.LOGIN, 30);
            stack.clock.plusSeconds(10);
            GenerateOtpResult second = stack.send(ANA, OtpProtocol.TOTP, Purpose.LOGIN, 30);

            assertThat(second.timeStep()).isEqualTo(first.timeStep());
            assertThat(second.demoCode()).isEqualTo(first.demoCode());
        }

        @Test
        void enUnaVentanaNuevaUnCodigoNuevo() {
            assertThat(HmacOtpAlgorithm.timeStep(Instant.ofEpochSecond(59), 30)).isEqualTo(1);
            assertThat(HmacOtpAlgorithm.timeStep(Instant.ofEpochSecond(60), 30)).isEqualTo(2);

            GenerateOtpResult first = stack.send(ANA, OtpProtocol.TOTP, Purpose.LOGIN, 30);
            stack.clock.plusSeconds(30);
            GenerateOtpResult second = stack.send(ANA, OtpProtocol.TOTP, Purpose.LOGIN, 30);
            byte[] secret = stack.secretOf(credential(HmacType.TOTP));

            assertThat(second.timeStep()).isEqualTo(first.timeStep() + 1);
            assertThat(second.timeStep()).isEqualTo(stack.clock.instant().getEpochSecond() / 30);
            assertThat(second.demoCode()).isEqualTo(HmacOtpAlgorithm.totp(secret, stack.clock.instant(), 30, 6));
        }

        @Test
        void unCodigoUsadoSeRechazaYQuedaRegistradaSuVentana() {
            GenerateOtpResult sent = stack.send(ANA, OtpProtocol.TOTP, Purpose.LOGIN, 30);
            stack.check(ANA, OtpProtocol.TOTP, Purpose.LOGIN, sent.demoCode());

            assertThat(credential(HmacType.TOTP).getLastUsedTimeStep()).isEqualTo(sent.timeStep());
            assertThatThrownBy(() -> stack.check(ANA, OtpProtocol.TOTP, Purpose.LOGIN, sent.demoCode()))
                    .isInstanceOf(OtpAlreadyUsedException.class);
        }

        // Tolerancia 1: con el reloj en T se aceptan T-1, T y T+1, como muestra el tablero.
        @Test
        void laVentanaAnteriorSigueValiendoPorTolerancia() {
            GenerateOtpResult sent = stack.send(ANA, OtpProtocol.TOTP, Purpose.LOGIN, 30);
            stack.clock.plusSeconds(30);

            assertThat(stack.check(ANA, OtpProtocol.TOTP, Purpose.LOGIN, sent.demoCode()).timeStep())
                    .isEqualTo(sent.timeStep());
        }

        @Test
        void laVentanaSiguienteTambienSeAcepta() {
            GenerateOtpResult sent = stack.send(ANA, OtpProtocol.TOTP, Purpose.LOGIN, 30);
            byte[] secret = stack.secretOf(credential(HmacType.TOTP));
            String next = HmacOtpAlgorithm.hotp(secret, sent.timeStep() + 1, 6);

            assertThat(stack.check(ANA, OtpProtocol.TOTP, Purpose.LOGIN, next).timeStep()).isEqualTo(sent.timeStep() + 1);
        }

        @Test
        void fueraDeLaToleranciaVence() {
            GenerateOtpResult sent = stack.send(ANA, OtpProtocol.TOTP, Purpose.LOGIN, 30);
            stack.clock.plusSeconds(60);

            assertThatThrownBy(() -> stack.check(ANA, OtpProtocol.TOTP, Purpose.LOGIN, sent.demoCode()))
                    .isInstanceOf(OtpExpiredException.class);
        }

        @Test
        void emitirNoTocaNingunContador() {
            for (int i = 0; i < 5; i++) {
                stack.send(ANA, OtpProtocol.TOTP, Purpose.LOGIN, 30);
                stack.clock.plusSeconds(30);
            }

            HmacCredential credential = credential(HmacType.TOTP);
            assertThat(credential.getIssuedCounter()).isZero();
            assertThat(credential.getCounter()).isZero();
        }
    }
}
