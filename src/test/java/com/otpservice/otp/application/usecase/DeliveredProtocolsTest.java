package com.otpservice.otp.application.usecase;

import com.otpservice.otp.adapter.config.HmacProperties;
import com.otpservice.otp.adapter.config.OtpProperties;
import com.otpservice.otp.adapter.out.persistence.memory.InMemoryCredentialAdapter;
import com.otpservice.otp.adapter.out.persistence.memory.InMemoryOtpPersistenceAdapter;
import com.otpservice.otp.adapter.out.security.AesGcmSecretCipher;
import com.otpservice.otp.adapter.out.security.CodeHasher;
import com.otpservice.otp.application.config.HmacSettings;
import com.otpservice.otp.application.config.OtpSettings;
import com.otpservice.otp.application.dto.GenerateOtpResult;
import com.otpservice.otp.application.dto.VerifyOtpResult;
import com.otpservice.otp.application.exception.OtpNotFoundException;
import com.otpservice.otp.application.port.in.GenerateOtpUseCase.GenerateOtpCommand;
import com.otpservice.otp.application.port.in.VerifyOtpUseCase.VerifyOtpCommand;
import com.otpservice.otp.application.port.out.CredentialPersistencePort;
import com.otpservice.otp.application.protocol.CodeProtocols;
import com.otpservice.otp.application.protocol.HotpProtocol;
import com.otpservice.otp.application.protocol.RandomCodeProtocol;
import com.otpservice.otp.application.protocol.TotpProtocol;
import com.otpservice.otp.domain.exception.InvalidCodeRequestException;
import com.otpservice.otp.domain.exception.InvalidOtpException;
import com.otpservice.otp.domain.exception.OtpAlreadyUsedException;
import com.otpservice.otp.domain.exception.OtpBlockedException;
import com.otpservice.otp.domain.exception.OtpExpiredException;
import com.otpservice.otp.domain.valueobject.EmailAddress;
import com.otpservice.otp.domain.valueobject.OtpCode;
import com.otpservice.otp.domain.valueobject.OtpProtocol;
import com.otpservice.otp.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveredProtocolsTest {

    private static final EmailAddress ANA = new EmailAddress("ana@gmail.com");

    private MutableClock clock;
    private InMemoryOtpPersistenceAdapter otpStore;
    private GenerateOtpUseCaseImpl generate;
    private VerifyOtpUseCaseImpl verify;
    private String lastMessage;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-01T14:00:10Z"));
        OtpProperties otp = new OtpProperties(6, 30, 3, 86400,
                "Tu código de verificación es %s. Vence en %d segundos.", "secreto", true, 10000, 0, 0, 600);
        HmacProperties hmacProperties = new HmacProperties(1, 10, HmacProperties.INSECURE_DEV_KEY);
        HmacSettings settings = new HmacSettings(1, 10);

        otpStore = new InMemoryOtpPersistenceAdapter(clock, otp);
        CodeHasher hasher = new CodeHasher(otp);
        OtpSettings otpSettings = otp.settings();
        CredentialPersistencePort credentials = new InMemoryCredentialAdapter(otp);
        AesGcmSecretCipher cipher = new AesGcmSecretCipher(hmacProperties);
        CodeProtocols protocols = new CodeProtocols(List.of(
                new RandomCodeProtocol(otpStore, hasher, otpSettings, clock),
                new HotpProtocol(credentials, cipher, settings, otpSettings, clock),
                new TotpProtocol(credentials, cipher, settings, otpSettings, clock)));
        generate = new GenerateOtpUseCaseImpl(protocols, otpSettings);
        verify = new VerifyOtpUseCaseImpl(protocols);
    }

    private GenerateOtpResult send(OtpProtocol protocol, Integer duration) {
        return generate.generate(new GenerateOtpCommand(ANA, protocol, 6, duration, null), (d, m) -> lastMessage = m);
    }

    private VerifyOtpResult check(OtpProtocol protocol, String code) {
        return verify.verify(new VerifyOtpCommand(ANA, protocol, new OtpCode(code)));
    }

    private static String wrong(String right) {
        return right.equals("000000") ? "000001" : "000000";
    }

    @Nested
    class Aleatorio {

        @Test
        void sigueFuncionandoComoAntesYGuardaElCodigo() {
            GenerateOtpResult sent = send(OtpProtocol.OTP, 30);

            assertThat(sent.expiresInSeconds()).isEqualTo(30);
            assertThat(otpStore.findLatestByDestination(ANA.getValue())).isPresent();
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

            assertThat(otpStore.findLatestByDestination(ANA.getValue())).isEmpty();
        }

        @Test
        void cadaEnvioAvanzaElContadorYElMensajeNoPrometeSegundos() {
            GenerateOtpResult first = send(OtpProtocol.HOTP, null);
            GenerateOtpResult second = send(OtpProtocol.HOTP, null);

            assertThat(second.counter()).isEqualTo(first.counter() + 1);
            assertThat(first.expiresInSeconds()).isNull();
            assertThat(lastMessage).contains("Sirve hasta que lo uses").doesNotContain("segundos");
        }

        @Test
        void noCaducaPorTiempo() {
            String code = send(OtpProtocol.HOTP, null).demoCode();
            clock.plusSeconds(7 * 24 * 3600);

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
        void tresFallosBloqueanHastaPedirOtro() {
            String code = send(OtpProtocol.HOTP, null).demoCode();
            assertThatThrownBy(() -> check(OtpProtocol.HOTP, wrong(code))).hasMessageContaining("intento 1 de 3");
            assertThatThrownBy(() -> check(OtpProtocol.HOTP, wrong(code))).hasMessageContaining("intento 2 de 3");
            assertThatThrownBy(() -> check(OtpProtocol.HOTP, wrong(code))).isInstanceOf(OtpBlockedException.class);
            assertThatThrownBy(() -> check(OtpProtocol.HOTP, code)).isInstanceOf(OtpBlockedException.class);

            String next = send(OtpProtocol.HOTP, null).demoCode();
            assertThat(check(OtpProtocol.HOTP, next).success()).isTrue();
        }

        @Test
        void soloAceptaEntreSeisYOchoDigitos() {
            assertThatThrownBy(() -> generate.generate(new GenerateOtpCommand(ANA, OtpProtocol.HOTP, 4, null, null), (d, m) -> {}))
                    .isInstanceOf(InvalidCodeRequestException.class);
        }
    }

    @Nested
    class Totp {

        @Test
        void noGuardaElCodigoEnviado() {
            send(OtpProtocol.TOTP, 30);

            assertThat(otpStore.findLatestByDestination(ANA.getValue())).isEmpty();
        }

        @Test
        void enLaMismaVentanaLlegaElMismoCodigo() {
            GenerateOtpResult first = send(OtpProtocol.TOTP, 30);
            clock.plusSeconds(5);
            GenerateOtpResult second = send(OtpProtocol.TOTP, 30);

            assertThat(second.demoCode()).isEqualTo(first.demoCode());
            assertThat(second.timeStep()).isEqualTo(first.timeStep());
        }

        @Test
        void venceAlTerminarSuVentanaMasLaTolerancia() {
            GenerateOtpResult sent = send(OtpProtocol.TOTP, 30);
            assertThat(sent.expiresInSeconds()).isEqualTo(50);

            clock.plusSeconds(sent.expiresInSeconds() + 1);
            assertThatThrownBy(() -> check(OtpProtocol.TOTP, sent.demoCode())).isInstanceOf(OtpExpiredException.class);
        }

        @Test
        void unCodigoVencidoNoGastaIntentos() {
            String old = send(OtpProtocol.TOTP, 30).demoCode();
            clock.plusSeconds(120);
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
