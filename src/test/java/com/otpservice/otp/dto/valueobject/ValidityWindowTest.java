package com.otpservice.otp.dto.valueobject;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidityWindowTest {

    private static final Instant GENERADO = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void esValidoDentroDeLaVentana() {
        ValidityWindow window = ValidityWindow.from(GENERADO, 30);
        assertThat(window.isExpired(Instant.parse("2026-01-01T10:00:20Z"))).isFalse();
    }

    @Test
    void expiraPasadaLaVentana() {
        ValidityWindow window = ValidityWindow.from(GENERADO, 30);
        assertThat(window.isExpired(Instant.parse("2026-01-01T10:00:31Z"))).isTrue();
    }

    @Test
    void rechazaVentanaInvertida() {
        assertThatThrownBy(() -> new ValidityWindow(GENERADO, GENERADO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
