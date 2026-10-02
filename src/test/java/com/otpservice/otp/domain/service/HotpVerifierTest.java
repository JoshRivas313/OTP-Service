package com.otpservice.otp.domain.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HotpVerifierTest {

    private static final byte[] SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final int WINDOW = 10;

    private static String codeAt(long counter) {
        return HmacOtpAlgorithm.hotp(SECRET, counter, 6);
    }

    // counter = 5 e issuedCounter = 9: estan pendientes los contadores 5, 6, 7 y 8.
    private static CodeMatch verify(long code) {
        return HotpVerifier.verify(SECRET, codeAt(code), 5, 9, WINDOW, 6);
    }

    @Test
    void aceptaCadaContadorEmitidoSinUsar() {
        for (long pending = 5; pending <= 8; pending++) {
            assertThat(verify(pending).matched()).isTrue();
            assertThat(verify(pending).value()).isEqualTo(pending);
        }
    }

    @Test
    void noAceptaUnContadorQueTodaviaNoSeEmitio() {
        assertThat(verify(9).outcome()).isEqualTo(CodeMatch.Outcome.NO_MATCH);
        assertThat(verify(12).outcome()).isEqualTo(CodeMatch.Outcome.NO_MATCH);
    }

    @Test
    void unContadorYaConsumidoSeReportaComoUsado() {
        assertThat(verify(4).outcome()).isEqualTo(CodeMatch.Outcome.REUSED);
        assertThat(verify(0).outcome()).isEqualTo(CodeMatch.Outcome.REUSED);
    }

    @Test
    void conMasPendientesQueLaVentanaSeAceptanLosMasRecientes() {
        // 12 emitidos sin usar (0..11) con ventana de 10: valen 2..11, y 0 y 1 quedaron reemplazados.
        for (long recent = 2; recent <= 11; recent++) {
            CodeMatch match = HotpVerifier.verify(SECRET, codeAt(recent), 0, 12, WINDOW, 6);
            assertThat(match.matched()).as("contador %d", recent).isTrue();
            assertThat(match.value()).isEqualTo(recent);
        }
        assertThat(HotpVerifier.verify(SECRET, codeAt(0), 0, 12, WINDOW, 6).outcome())
                .isEqualTo(CodeMatch.Outcome.SUPERSEDED);
        assertThat(HotpVerifier.verify(SECRET, codeAt(1), 0, 12, WINDOW, 6).outcome())
                .isEqualTo(CodeMatch.Outcome.SUPERSEDED);
    }

    @Test
    void sinCodigosPendientesNadaCoincide() {
        assertThat(HotpVerifier.verify(SECRET, codeAt(3), 3, 3, WINDOW, 6).outcome())
                .isEqualTo(CodeMatch.Outcome.NO_MATCH);
        assertThat(HotpVerifier.verify(SECRET, codeAt(2), 3, 3, WINDOW, 6).outcome())
                .isEqualTo(CodeMatch.Outcome.REUSED);
    }

    @Test
    void elRfc4226CoincideDesdeElContadorCero() {
        assertThat(HotpVerifier.verify(SECRET, "755224", 0, 10, WINDOW, 6).value()).isZero();
        assertThat(HotpVerifier.verify(SECRET, "520489", 0, 10, WINDOW, 6).value()).isEqualTo(9);
    }
}
