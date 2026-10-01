package com.otpservice.otp.application.usecase;

import com.otpservice.otp.adapter.config.AuthenticatorProperties;
import com.otpservice.otp.adapter.config.OtpProperties;
import com.otpservice.otp.adapter.out.persistence.memory.InMemoryCredentialAdapter;
import com.otpservice.otp.adapter.out.qr.ZxingQrCodeAdapter;
import com.otpservice.otp.adapter.out.security.AesGcmSecretCipher;
import com.otpservice.otp.application.config.AuthenticatorSettings;
import com.otpservice.otp.application.dto.AuthenticatorVerifyResult;
import com.otpservice.otp.application.dto.EnrollmentResult;
import com.otpservice.otp.application.exception.EnrollmentNotFoundException;
import com.otpservice.otp.application.port.in.AuthenticatorCodeCommand;
import com.otpservice.otp.application.port.in.EnrollAuthenticatorUseCase.EnrollCommand;
import com.otpservice.otp.domain.exception.OtpAlreadyUsedException;
import com.otpservice.otp.domain.exception.AuthenticatorLockedException;
import com.otpservice.otp.domain.exception.EnrollmentNotConfirmedException;
import com.otpservice.otp.domain.exception.InvalidOtpException;
import com.otpservice.otp.domain.exception.InvalidCodeRequestException;
import com.otpservice.otp.domain.service.HmacOtpAlgorithm;
import com.otpservice.otp.domain.valueobject.HmacType;
import com.otpservice.otp.domain.valueobject.EmailAddress;
import com.otpservice.otp.support.TestBase32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticatorUseCasesTest {

    private static final EmailAddress ANA = new EmailAddress("ana@gmail.com");

    private MutableClock clock;
    private EnrollAuthenticatorUseCaseImpl enroll;
    private ConfirmEnrollmentUseCaseImpl confirm;
    private VerifyAuthenticatorCodeUseCaseImpl verify;
    private RemoveEnrollmentUseCaseImpl remove;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-09-30T14:00:10Z"));
        OtpProperties otpProperties = new OtpProperties(6, 30, 3, 86400, "%s %d", "secreto", false, 10000, 5, 20, 600);
        AuthenticatorProperties properties =
                new AuthenticatorProperties("OTP Service", 1, 10, 5, 600, 600, AuthenticatorProperties.INSECURE_DEV_KEY);
        AuthenticatorSettings settings = new AuthenticatorSettings("OTP Service", 1, 10, 5, 600);

        InMemoryCredentialAdapter persistence = new InMemoryCredentialAdapter(otpProperties);
        AesGcmSecretCipher cipher = new AesGcmSecretCipher(properties);
        AuthenticatorCodeChecker checker = new AuthenticatorCodeChecker(persistence, cipher, settings, clock);

        enroll = new EnrollAuthenticatorUseCaseImpl(persistence, cipher, new ZxingQrCodeAdapter(), settings, clock);
        confirm = new ConfirmEnrollmentUseCaseImpl(persistence, checker);
        verify = new VerifyAuthenticatorCodeUseCaseImpl(persistence, checker);
        remove = new RemoveEnrollmentUseCaseImpl(persistence);
    }

    private AuthenticatorCodeCommand code(HmacType type, String code) {
        return new AuthenticatorCodeCommand(ANA, type, code);
    }

    private String totpFromApp(EnrollmentResult enrollment, Instant at) {
        return HmacOtpAlgorithm.totp(TestBase32.decode(enrollment.secretBase32()), at, enrollment.periodSeconds(), enrollment.digits());
    }

    private String hotpFromApp(EnrollmentResult enrollment, long counter) {
        return HmacOtpAlgorithm.hotp(TestBase32.decode(enrollment.secretBase32()), counter, enrollment.digits());
    }

    private static String wrongCode(String right) {
        return right.equals("000000") ? "000001" : "000000";
    }

    @Nested
    class Registro {

        @Test
        void devuelveQrUriYSecretoParaLaApp() {
            EnrollmentResult result = enroll.enroll(new EnrollCommand(ANA, HmacType.TOTP, null, null));

            assertThat(result.secretBase32()).hasSize(32);
            assertThat(result.otpauthUri())
                    .startsWith("otpauth://totp/OTP%20Service:ana%40gmail.com?secret=" + result.secretBase32())
                    .endsWith("&digits=6&period=30");
            assertThat(result.qrSvg()).startsWith("<svg");
        }

        @Test
        void cadaRegistroTieneSuPropioSecreto() {
            String first = enroll.enroll(new EnrollCommand(ANA, HmacType.TOTP, 6, 30)).secretBase32();
            String second = enroll.enroll(new EnrollCommand(ANA, HmacType.TOTP, 6, 30)).secretBase32();

            assertThat(first).isNotEqualTo(second);
        }

        @Test
        void rechazaDigitosYPeriodosQueLasAppsNoSoportan() {
            assertThatThrownBy(() -> enroll.enroll(new EnrollCommand(ANA, HmacType.TOTP, 7, 30)))
                    .isInstanceOf(InvalidCodeRequestException.class);
            assertThatThrownBy(() -> enroll.enroll(new EnrollCommand(ANA, HmacType.TOTP, 6, 45)))
                    .isInstanceOf(InvalidCodeRequestException.class);
        }

        @Test
        void volverARegistrarAnulaLaAppAnterior() {
            EnrollmentResult old = enroll.enroll(new EnrollCommand(ANA, HmacType.TOTP, 6, 30));
            confirm.confirm(code(HmacType.TOTP, totpFromApp(old, clock.instant())));
            EnrollmentResult renewed = enroll.enroll(new EnrollCommand(ANA, HmacType.TOTP, 6, 30));
            clock.plusSeconds(30);

            assertThatThrownBy(() -> confirm.confirm(code(HmacType.TOTP, totpFromApp(old, clock.instant()))))
                    .isInstanceOf(InvalidOtpException.class);
            assertThat(confirm.confirm(code(HmacType.TOTP, totpFromApp(renewed, clock.instant()))).success()).isTrue();
        }
    }

    @Nested
    class Totp {

        private EnrollmentResult app;

        @BeforeEach
        void registrar() {
            app = enroll.enroll(new EnrollCommand(ANA, HmacType.TOTP, 6, 30));
        }

        @Test
        void sinConfirmarNoSirveParaIniciarSesion() {
            assertThatThrownBy(() -> verify.verify(code(HmacType.TOTP, totpFromApp(app, clock.instant()))))
                    .isInstanceOf(EnrollmentNotConfirmedException.class);
        }

        @Test
        void seConfirmaConElPrimerCodigoDeLaApp() {
            AuthenticatorVerifyResult result = confirm.confirm(code(HmacType.TOTP, totpFromApp(app, clock.instant())));

            assertThat(result.success()).isTrue();
            assertThat(result.timeStep()).isEqualTo(HmacOtpAlgorithm.timeStep(clock.instant(), 30));
        }

        @Test
        void iniciaSesionSinPedirNadaAlServidor() {
            confirm.confirm(code(HmacType.TOTP, totpFromApp(app, clock.instant())));
            clock.plusSeconds(30);

            assertThat(verify.verify(code(HmacType.TOTP, totpFromApp(app, clock.instant()))).success()).isTrue();
        }

        @Test
        void elMismoCodigoNoSirveDosVeces() {
            String current = totpFromApp(app, clock.instant());
            confirm.confirm(code(HmacType.TOTP, current));

            assertThatThrownBy(() -> verify.verify(code(HmacType.TOTP, current)))
                    .isInstanceOf(OtpAlreadyUsedException.class);
        }

        @Test
        void toleraUnaAppConElRelojAdelantado25Segundos() {
            assertThat(confirm.confirm(code(HmacType.TOTP, totpFromApp(app, clock.instant().plusSeconds(25)))).success())
                    .isTrue();
        }

        @Test
        void unCodigoDeHaceDosMinutosYaNoSirve() {
            String old = totpFromApp(app, clock.instant());
            clock.plusSeconds(120);

            assertThatThrownBy(() -> confirm.confirm(code(HmacType.TOTP, old)))
                    .isInstanceOf(InvalidOtpException.class);
        }

        @Test
        void seBloqueaTrasCincoFallosAunqueDespuesLlegueElCodigoCorrecto() {
            String right = totpFromApp(app, clock.instant());
            for (int i = 1; i <= 4; i++) {
                assertThatThrownBy(() -> confirm.confirm(code(HmacType.TOTP, wrongCode(right))))
                        .isInstanceOf(InvalidOtpException.class)
                        .hasMessageContaining("intento " + i + " de 5");
            }
            assertThatThrownBy(() -> confirm.confirm(code(HmacType.TOTP, wrongCode(right))))
                    .isInstanceOf(AuthenticatorLockedException.class);
            assertThatThrownBy(() -> confirm.confirm(code(HmacType.TOTP, right)))
                    .isInstanceOf(AuthenticatorLockedException.class);

            clock.plusSeconds(601);
            assertThat(confirm.confirm(code(HmacType.TOTP, totpFromApp(app, clock.instant()))).success()).isTrue();
        }

        @Test
        void unAciertoReiniciaLosFallos() {
            String right = totpFromApp(app, clock.instant());
            for (int i = 0; i < 4; i++) {
                assertThatThrownBy(() -> confirm.confirm(code(HmacType.TOTP, wrongCode(right))))
                        .isInstanceOf(InvalidOtpException.class);
            }
            confirm.confirm(code(HmacType.TOTP, right));
            clock.plusSeconds(30);
            String next = totpFromApp(app, clock.instant());

            assertThatThrownBy(() -> verify.verify(code(HmacType.TOTP, wrongCode(next))))
                    .hasMessageContaining("intento 1 de 5");
        }

        @Test
        void dosPeticionesSimultaneasConElMismoCodigoSoloAceptanUna() throws Exception {
            confirm.confirm(code(HmacType.TOTP, totpFromApp(app, clock.instant())));
            clock.plusSeconds(30);
            String shared = totpFromApp(app, clock.instant());

            int threads = 8;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<Boolean> attempt = () -> {
                    start.await();
                    try {
                        return verify.verify(code(HmacType.TOTP, shared)).success();
                    } catch (OtpAlreadyUsedException exception) {
                        return false;
                    }
                };
                results.add(pool.submit(attempt));
            }
            start.countDown();
            int accepted = 0;
            for (Future<Boolean> result : results) {
                accepted += result.get() ? 1 : 0;
            }
            pool.shutdown();

            assertThat(accepted).isEqualTo(1);
        }
    }

    @Nested
    class Hotp {

        private EnrollmentResult app;

        @BeforeEach
        void registrarYConfirmar() {
            app = enroll.enroll(new EnrollCommand(ANA, HmacType.HOTP, 6, null));
            confirm.confirm(code(HmacType.HOTP, hotpFromApp(app, 0)));
        }

        @Test
        void elQrEmpiezaEnElContadorCero() {
            assertThat(app.otpauthUri()).startsWith("otpauth://hotp/").endsWith("&counter=0");
            assertThat(app.counter()).isZero();
        }

        @Test
        void aceptaElSiguienteCodigoDeLaApp() {
            assertThat(verify.verify(code(HmacType.HOTP, hotpFromApp(app, 1))).counter()).isEqualTo(1);
        }

        @Test
        void noCaducaPorTiempo() {
            clock.plusSeconds(7 * 24 * 3600);

            assertThat(verify.verify(code(HmacType.HOTP, hotpFromApp(app, 1))).success()).isTrue();
        }

        @Test
        void aceptaCodigosGeneradosEnLaAppSinUsarYSeResincroniza() {
            assertThat(verify.verify(code(HmacType.HOTP, hotpFromApp(app, 4))).counter()).isEqualTo(4);
            assertThat(verify.verify(code(HmacType.HOTP, hotpFromApp(app, 5))).counter()).isEqualTo(5);
        }

        @Test
        void losCodigosSaltadosQuedanInvalidados() {
            verify.verify(code(HmacType.HOTP, hotpFromApp(app, 4)));

            assertThatThrownBy(() -> verify.verify(code(HmacType.HOTP, hotpFromApp(app, 2))))
                    .isInstanceOf(OtpAlreadyUsedException.class);
        }

        @Test
        void unCodigoMasAllaDelAdelantoNoSirve() {
            assertThatThrownBy(() -> verify.verify(code(HmacType.HOTP, hotpFromApp(app, 1 + 11))))
                    .isInstanceOf(InvalidOtpException.class);
        }
    }

    @Test
    void eliminarElRegistroImpideIniciarSesion() {
        EnrollmentResult app = enroll.enroll(new EnrollCommand(ANA, HmacType.TOTP, 6, 30));
        confirm.confirm(code(HmacType.TOTP, totpFromApp(app, clock.instant())));

        remove.remove(ANA, HmacType.TOTP);

        clock.plusSeconds(30);
        assertThatThrownBy(() -> verify.verify(code(HmacType.TOTP, totpFromApp(app, clock.instant()))))
                .isInstanceOf(EnrollmentNotFoundException.class);
        assertThatThrownBy(() -> remove.remove(ANA, HmacType.TOTP))
                .isInstanceOf(EnrollmentNotFoundException.class);
    }

    @Test
    void totpYHotpDelMismoCorreoSonIndependientes() {
        EnrollmentResult totp = enroll.enroll(new EnrollCommand(ANA, HmacType.TOTP, 6, 30));
        EnrollmentResult hotp = enroll.enroll(new EnrollCommand(ANA, HmacType.HOTP, 6, null));

        assertThat(confirm.confirm(code(HmacType.TOTP, totpFromApp(totp, clock.instant()))).success()).isTrue();
        assertThat(confirm.confirm(code(HmacType.HOTP, hotpFromApp(hotp, 0))).success()).isTrue();
    }

    static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void plusSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
