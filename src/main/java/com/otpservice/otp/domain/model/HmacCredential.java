package com.otpservice.otp.domain.model;

import com.otpservice.otp.domain.valueobject.CredentialStatus;
import com.otpservice.otp.domain.valueobject.CredentialMode;
import com.otpservice.otp.domain.valueobject.EncryptedSecret;
import com.otpservice.otp.domain.valueobject.HmacType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;

// counter: proximo contador que se acepta. issuedCounter: proximo que se emite (solo DELIVERED).
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class HmacCredential {

    public static final long NO_TIME_STEP_USED = -1;

    private final String id;
    private final String destination;
    private final HmacType type;
    private final CredentialMode mode;
    private final EncryptedSecret secret;
    private final int digits;
    private final int periodSeconds;
    private final long counter;
    private final long issuedCounter;
    private final long lastUsedTimeStep;
    private final CredentialStatus status;
    private final int failedAttempts;
    private final Instant lockedUntil;
    private final Instant createdAt;
    private final Instant confirmedAt;

    public static HmacCredential pending(String destination, HmacType type, EncryptedSecret secret,
                                         int digits, int periodSeconds, Instant now) {
        return base(destination, type, CredentialMode.APP, secret, digits, periodSeconds, now)
                .status(CredentialStatus.PENDING)
                .build();
    }

    public static HmacCredential delivered(String destination, HmacType type, EncryptedSecret secret,
                                           int digits, int periodSeconds, Instant now) {
        return base(destination, type, CredentialMode.DELIVERED, secret, digits, periodSeconds, now)
                .status(CredentialStatus.ACTIVE)
                .confirmedAt(now)
                .build();
    }

    private static HmacCredentialBuilder base(String destination, HmacType type, CredentialMode mode,
                                              EncryptedSecret secret, int digits, int periodSeconds, Instant now) {
        return HmacCredential.builder()
                .destination(destination)
                .type(type)
                .mode(mode)
                .secret(secret)
                .digits(digits)
                .periodSeconds(type == HmacType.TOTP ? periodSeconds : 0)
                .counter(0)
                .issuedCounter(0)
                .lastUsedTimeStep(NO_TIME_STEP_USED)
                .failedAttempts(0)
                .createdAt(now);
    }

    public static String secretContext(String destination, HmacType type, CredentialMode mode) {
        return destination + "|" + type.name() + "|" + mode.name();
    }

    public String secretContext() {
        return secretContext(destination, type, mode);
    }

    public long pendingCodes() {
        return Math.max(0, issuedCounter - counter);
    }

    public boolean isPending() {
        return status == CredentialStatus.PENDING;
    }

    public boolean isActive() {
        return status == CredentialStatus.ACTIVE;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    public long minutesLocked(Instant now) {
        return isLocked(now) ? Math.max(1, Duration.between(now, lockedUntil).toMinutes() + 1) : 0;
    }

    @Override
    public String toString() {
        return "HmacCredential[id=%s, type=%s, mode=%s, status=%s]".formatted(id, type, mode, status);
    }
}
