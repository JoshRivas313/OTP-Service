package com.otpservice.otp.domain.model;

import com.otpservice.otp.domain.exception.OtpAlreadyUsedException;
import com.otpservice.otp.domain.exception.OtpBlockedException;
import com.otpservice.otp.domain.exception.OtpExpiredException;
import com.otpservice.otp.domain.exception.OtpInvalidatedException;
import com.otpservice.otp.domain.valueobject.Cellphone;
import com.otpservice.otp.domain.valueobject.ValidityWindow;
import com.otpservice.otp.domain.valueobject.VerificationStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtpTest {

    private static final Instant NOW = Instant.parse("2026-10-01T14:00:00Z");
    private static final int MAX_ATTEMPTS = 3;

    private static Otp otp(VerificationStatus status, Instant expiresAt) {
        return Otp.builder()
                .id("1")
                .destination(new Cellphone("987654321").getValue())
                .codeHash("hash")
                .digits(6)
                .validityWindow(new ValidityWindow(NOW.minusSeconds(30), expiresAt))
                .verificationStatus(status)
                .purgeAt(expiresAt.plusSeconds(3600))
                .build();
    }

    private static Otp active() {
        return otp(new VerificationStatus(), NOW.plusSeconds(30));
    }

    @Test
    void aFreshCodeCanBeVerified() {
        assertThatCode(() -> active().ensureVerifiable(NOW, MAX_ATTEMPTS)).doesNotThrowAnyException();
    }

    @Test
    void anInvalidatedCodeIsRejected() {
        Otp otp = otp(new VerificationStatus(0, false, true), NOW.plusSeconds(30));
        assertThatThrownBy(() -> otp.ensureVerifiable(NOW, MAX_ATTEMPTS)).isInstanceOf(OtpInvalidatedException.class);
    }

    @Test
    void aUsedCodeIsRejected() {
        Otp otp = otp(new VerificationStatus(0, true, false), NOW.plusSeconds(30));
        assertThatThrownBy(() -> otp.ensureVerifiable(NOW, MAX_ATTEMPTS)).isInstanceOf(OtpAlreadyUsedException.class);
    }

    @Test
    void anExpiredCodeIsRejected() {
        assertThatThrownBy(() -> active().ensureVerifiable(NOW.plusSeconds(31), MAX_ATTEMPTS))
                .isInstanceOf(OtpExpiredException.class);
    }

    @Test
    void aCodeWithAllAttemptsSpentIsBlocked() {
        Otp otp = otp(new VerificationStatus(MAX_ATTEMPTS, false, false), NOW.plusSeconds(30));
        assertThatThrownBy(() -> otp.ensureVerifiable(NOW, MAX_ATTEMPTS)).isInstanceOf(OtpBlockedException.class);
    }

    @Test
    void invalidatedWinsOverUsedAndUsedWinsOverExpired() {
        Otp invalidatedAndUsed = otp(new VerificationStatus(0, true, true), NOW.plusSeconds(30));
        assertThatThrownBy(() -> invalidatedAndUsed.ensureVerifiable(NOW, MAX_ATTEMPTS))
                .isInstanceOf(OtpInvalidatedException.class);

        Otp usedAndExpired = otp(new VerificationStatus(0, true, false), NOW.plusSeconds(30));
        assertThatThrownBy(() -> usedAndExpired.ensureVerifiable(NOW.plusSeconds(60), MAX_ATTEMPTS))
                .isInstanceOf(OtpAlreadyUsedException.class);
    }

    @Test
    void expiredWinsOverBlocked() {
        Otp otp = otp(new VerificationStatus(MAX_ATTEMPTS, false, false), NOW.plusSeconds(30));
        assertThatThrownBy(() -> otp.ensureVerifiable(NOW.plusSeconds(60), MAX_ATTEMPTS))
                .isInstanceOf(OtpExpiredException.class);
    }
}
