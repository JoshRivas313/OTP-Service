package com.otpservice.otp.dto.valueobject;

public final class VerificationStatus {

    private int attempts;
    private boolean used;
    private boolean invalidated;

    public VerificationStatus() {
        this(0, false, false);
    }

    public VerificationStatus(int attempts, boolean used, boolean invalidated) {
        this.attempts = attempts;
        this.used = used;
        this.invalidated = invalidated;
    }

    public void incrementAttempts() {
        attempts++;
    }

    public void markAsUsed() {
        used = true;
    }

    public void invalidate() {
        invalidated = true;
    }

    public boolean isBlocked(int maxAllowedAttempts) {
        return attempts >= maxAllowedAttempts;
    }

    public int getAttempts() {
        return attempts;
    }

    public boolean isUsed() {
        return used;
    }

    public boolean isInvalidated() {
        return invalidated;
    }
}
