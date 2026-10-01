package com.otpservice.otp.domain.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HotpVerifierTest {

    private static final byte[] SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    private static String codeAt(long counter) {
        return HmacOtpAlgorithm.hotp(SECRET, counter, 6);
    }

    @Test
    void aceptaElContadorEsperado() {
        CodeMatch match = HotpVerifier.verify(SECRET, codeAt(5), 5, 10, 10, 6);

        assertThat(match.matched()).isTrue();
        assertThat(match.value()).isEqualTo(5);
    }

    @Test
    void aceptaCodigosGeneradosEnLaAppSinUsarDentroDelAdelanto() {
        CodeMatch match = HotpVerifier.verify(SECRET, codeAt(8), 5, 10, 10, 6);

        assertThat(match.value()).isEqualTo(8);
    }

    @Test
    void rechazaLoQueSuperaElAdelanto() {
        assertThat(HotpVerifier.verify(SECRET, codeAt(16), 5, 10, 10, 6).outcome())
                .isEqualTo(CodeMatch.Outcome.NO_MATCH);
    }

    @Test
    void unCodigoDeUnContadorYaSuperadoSeReportaComoUsado() {
        assertThat(HotpVerifier.verify(SECRET, codeAt(4), 5, 10, 10, 6).outcome())
                .isEqualTo(CodeMatch.Outcome.REUSED);
    }

    @Test
    void elRfc4226CoincideDesdeElContadorCero() {
        assertThat(HotpVerifier.verify(SECRET, "755224", 0, 10, 10, 6).value()).isZero();
        assertThat(HotpVerifier.verify(SECRET, "520489", 0, 10, 10, 6).value()).isEqualTo(9);
    }
}
