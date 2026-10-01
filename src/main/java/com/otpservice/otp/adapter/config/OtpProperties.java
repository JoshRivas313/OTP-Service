package com.otpservice.otp.adapter.config;

import com.otpservice.otp.application.config.OtpSettings;
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
        @NotBlank String hashSecret,
        @DefaultValue("false") boolean demoMode,
        @DefaultValue("10000") int memoryMaxEntries,
        @DefaultValue("5") int rateLimitPerDestination,
        @DefaultValue("20") int rateLimitPerIp,
        @DefaultValue("600") int rateLimitWindowSeconds
) {
    public static final String INSECURE_DEV_SECRET = "dev-only-secret-change-me";

    public OtpSettings settings() {
        return new OtpSettings(digits, durationSeconds, maxAttempts, retentionSeconds, messageTemplate, demoMode);
    }

    public boolean usingInsecureDevSecret() {
        return INSECURE_DEV_SECRET.equals(hashSecret);
    }
}
