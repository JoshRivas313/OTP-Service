package com.otpservice.otp.adapter.config;

import com.otpservice.otp.adapter.exception.RateLimitExceededException;
import com.otpservice.otp.domain.exception.ErrorCode;
import com.otpservice.otp.support.MutableClock;
import com.otpservice.otp.support.TestOtpProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SendLimitsTest {

    private MutableClock clock;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-06T12:00:00Z"));
    }

    @Test
    void laCuotaDiariaCuentaLosEnviosDeTodosLosDestinos() {
        DailySendQuota quota = new DailySendQuota(clock, 3);

        assertThat(quota.tryAcquire()).isTrue();
        assertThat(quota.tryAcquire()).isTrue();
        assertThat(quota.tryAcquire()).isTrue();
        assertThat(quota.tryAcquire()).isFalse();
    }

    @Test
    void laCuotaSeRecuperaCuandoPasanVeinticuatroHoras() {
        DailySendQuota quota = new DailySendQuota(clock, 1);
        assertThat(quota.tryAcquire()).isTrue();

        clock.plusSeconds(24 * 3600 - 1);
        assertThat(quota.tryAcquire()).isFalse();

        clock.plusSeconds(2);
        assertThat(quota.tryAcquire()).isTrue();
    }

    @Test
    void unLimiteDeCeroLaDesactiva() {
        DailySendQuota quota = new DailySendQuota(clock, 0);

        for (int i = 0; i < 1000; i++) {
            assertThat(quota.tryAcquire()).isTrue();
        }
    }

    @Test
    void alAgotarseLaCuotaElErrorEsUn503ConUnMensajeAmable() {
        SendRateLimiter limiter = new SendRateLimiter(TestOtpProperties.otp().dailySendLimit(2).build(), clock);
        limiter.check("a@gmail.com", "1.1.1.1");
        limiter.check("b@gmail.com", "2.2.2.2");

        assertThatThrownBy(() -> limiter.check("c@gmail.com", "3.3.3.3"))
                .isInstanceOfSatisfying(RateLimitExceededException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.DAILY_QUOTA_EXCEEDED);
                    assertThat(exception.getMessage()).contains("agotó sus envíos de hoy").contains("tablero");
                });
    }

    @Test
    void unEnvioRechazadoPorDestinoNoGastaLaCuotaDiaria() {
        SendRateLimiter limiter = new SendRateLimiter(
                TestOtpProperties.otp().rateLimitPerDestination(1).dailySendLimit(2).build(), clock);

        limiter.check("a@gmail.com", "1.1.1.1");                       // cuota 1 de 2
        assertThatThrownBy(() -> limiter.check("a@gmail.com", "1.1.1.1"))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED));
        limiter.check("b@gmail.com", "2.2.2.2");                       // cuota 2 de 2: el rechazo no gasto ninguna

        assertThatThrownBy(() -> limiter.check("c@gmail.com", "3.3.3.3"))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.DAILY_QUOTA_EXCEEDED));
    }

    @Test
    void devolverUnaPlazaPermiteElReintentoPorDestinoEIp() {
        SendRateLimiter limiter = new SendRateLimiter(
                TestOtpProperties.otp().rateLimitPerDestination(1).rateLimitPerIp(1).dailySendLimit(1).build(), clock);

        limiter.check("a@gmail.com", "1.1.1.1");
        limiter.refund("a@gmail.com", "1.1.1.1");

        limiter.check("a@gmail.com", "1.1.1.1");          // sin devolucion fallaria en los tres limites
        assertThatThrownBy(() -> limiter.check("a@gmail.com", "1.1.1.1"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void devolverSinHaberEnviadoNadaNoHaceDanio() {
        SendRateLimiter limiter = new SendRateLimiter(TestOtpProperties.otp().dailySendLimit(1).build(), clock);

        limiter.refund("a@gmail.com", "1.1.1.1");
        limiter.check("a@gmail.com", "1.1.1.1");

        assertThatThrownBy(() -> limiter.check("b@gmail.com", "2.2.2.2"))
                .isInstanceOfSatisfying(RateLimitExceededException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.DAILY_QUOTA_EXCEEDED));
    }
}
