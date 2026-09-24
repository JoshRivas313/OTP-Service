package com.otpservice.otp.dto.valueobject;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VerificationStatusTest {

    @Test
    void empiezaSinIntentos() {
        VerificationStatus status = new VerificationStatus();
        assertThat(status.getAttempts()).isZero();
        assertThat(status.isBlocked(3)).isFalse();
    }

    @Test
    void seBloqueaAlLlegarAlMaximo() {
        VerificationStatus status = new VerificationStatus();
        status.incrementAttempts();
        status.incrementAttempts();
        status.incrementAttempts();
        assertThat(status.isBlocked(3)).isTrue();
    }

    @Test
    void marcarComoUsado() {
        VerificationStatus status = new VerificationStatus();
        status.markAsUsed();
        assertThat(status.isUsed()).isTrue();
    }
}
