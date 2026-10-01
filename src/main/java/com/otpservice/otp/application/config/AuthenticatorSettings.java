package com.otpservice.otp.application.config;

public record AuthenticatorSettings(
        String issuer,
        int totpToleranceSteps,
        int hotpLookAhead,
        int maxFailedAttempts,
        int lockSeconds
) {
}
