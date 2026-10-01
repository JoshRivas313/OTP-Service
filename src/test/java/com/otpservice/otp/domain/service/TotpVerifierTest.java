package com.otpservice.otp.domain.service;

import com.otpservice.otp.domain.model.HmacCredential;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpVerifierTest {

    private static final byte[] SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final Instant NOW = Instant.parse("2026-09-30T14:00:10Z");
    private static final long CURRENT = HmacOtpAlgorithm.timeStep(NOW, 30);
    private static final long NONE = HmacCredential.NO_TIME_STEP_USED;

    private static String codeAt(long step) {
        return HmacOtpAlgorithm.hotp(SECRET, step, 6);
    }

    @Test
    void aceptaElCodigoDeLaVentanaActual() {
        CodeMatch match = TotpVerifier.verify(SECRET, codeAt(CURRENT), NOW, 30, 6, 1, NONE);

        assertThat(match.matched()).isTrue();
        assertThat(match.value()).isEqualTo(CURRENT);
    }

    @Test
    void aceptaLasVentanasVecinasPorLaTolerancia() {
        assertThat(TotpVerifier.verify(SECRET, codeAt(CURRENT - 1), NOW, 30, 6, 1, NONE).value()).isEqualTo(CURRENT - 1);
        assertThat(TotpVerifier.verify(SECRET, codeAt(CURRENT + 1), NOW, 30, 6, 1, NONE).value()).isEqualTo(CURRENT + 1);
    }

    @Test
    void rechazaLoQueQuedaFueraDeLaTolerancia() {
        assertThat(TotpVerifier.verify(SECRET, codeAt(CURRENT - 2), NOW, 30, 6, 1, NONE).outcome())
                .isEqualTo(CodeMatch.Outcome.NO_MATCH);
        assertThat(TotpVerifier.verify(SECRET, codeAt(CURRENT + 2), NOW, 30, 6, 1, NONE).outcome())
                .isEqualTo(CodeMatch.Outcome.NO_MATCH);
    }

    @Test
    void elMismoCodigoNoSirveDosVeces() {
        CodeMatch second = TotpVerifier.verify(SECRET, codeAt(CURRENT), NOW, 30, 6, 1, CURRENT);

        assertThat(second.outcome()).isEqualTo(CodeMatch.Outcome.REUSED);
    }

    @Test
    void unaVentanaAnteriorALaUltimaUsadaTampocoSirve() {
        CodeMatch match = TotpVerifier.verify(SECRET, codeAt(CURRENT - 1), NOW, 30, 6, 1, CURRENT);

        assertThat(match.outcome()).isEqualTo(CodeMatch.Outcome.REUSED);
    }

    @Test
    void unCodigoInventadoNoCoincide() {
        String wrong = codeAt(CURRENT).equals("000000") ? "000001" : "000000";

        assertThat(TotpVerifier.verify(SECRET, wrong, NOW, 30, 6, 1, NONE).outcome())
                .isEqualTo(CodeMatch.Outcome.NO_MATCH);
    }
}
