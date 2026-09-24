package com.otpservice.otp.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "otp")
public record OtpProperties(
        @DefaultValue("6") int digits,
        @DefaultValue("30") int durationSeconds,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("86400") int retentionSeconds,
        @DefaultValue("Tu código de verificación es %s. Vence en %d segundos.") String messageTemplate,
        @NotBlank String hashSecret
) {
    public static final String INSECURE_DEV_SECRET = "dev-only-secret-change-me";

    public boolean usingInsecureDevSecret() {
        return INSECURE_DEV_SECRET.equals(hashSecret);
    }
}
