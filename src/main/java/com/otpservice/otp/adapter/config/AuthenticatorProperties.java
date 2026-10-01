package com.otpservice.otp.adapter.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "authenticator")
public record AuthenticatorProperties(
        @DefaultValue("Un Solo Uso") @NotBlank String issuer,
        @DefaultValue("1") @Min(0) int totpToleranceSteps,
        @DefaultValue("10") @Min(0) int hotpLookAhead,
        @DefaultValue("5") @Min(1) int maxFailedAttempts,
        @DefaultValue("600") @Min(1) int lockSeconds,
        @DefaultValue("600") @Min(1) int emailVerificationMaxAgeSeconds,
        @NotBlank String encryptionKey
) {
    public static final String INSECURE_DEV_KEY = "ZGV2LW9ubHktZW5jcnlwdGlvbi1rZXktY2hhbmdlbWU=";

    public boolean usingInsecureDevKey() {
        return INSECURE_DEV_KEY.equals(encryptionKey);
    }
}
