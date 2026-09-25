package com.otpservice.otp.domain.valueobject;

import java.time.Instant;

public final class ValidityWindow {

    private final Instant generatedAt;
    private final Instant expiresAt;

    public ValidityWindow(Instant generatedAt, Instant expiresAt) {
        if (generatedAt == null || expiresAt == null) {
            throw new IllegalArgumentException("generatedAt y expiresAt no pueden ser null");
        }
        if (!expiresAt.isAfter(generatedAt)) {
            throw new IllegalArgumentException("expiresAt debe ser posterior a generatedAt");
        }
        this.generatedAt = generatedAt;
        this.expiresAt = expiresAt;
    }

    public static ValidityWindow from(Instant generatedAt, int durationSeconds) {
        return new ValidityWindow(generatedAt, generatedAt.plusSeconds(durationSeconds));
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
