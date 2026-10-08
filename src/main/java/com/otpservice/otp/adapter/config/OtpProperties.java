package com.otpservice.otp.adapter.config;

import com.otpservice.otp.application.config.OtpSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

// hashSecret no lleva @NotBlank: lo exige RequiredSecretsGuard, con un mensaje que dice como arrancar en desarrollo.
// retentionSeconds: cuanto se conserva un codigo aleatorio tras vencer. credentialRetentionSeconds: cuanto se conserva
// la credencial de HOTP/TOTP de un destino sin actividad (30 dias): guarda su correo o celular, asi que no es para siempre.
// Los valores por defecto son los seguros: sin codigos en el log, sin mensaje personalizado y sin limite diario.
@Validated
@ConfigurationProperties(prefix = "otp")
public record OtpProperties(
        @DefaultValue("6") int digits,
        @DefaultValue("30") int durationSeconds,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("86400") int retentionSeconds,
        @DefaultValue("2592000") int credentialRetentionSeconds,
        String hashSecret,
        @DefaultValue("false") boolean demoMode,
        @DefaultValue("10000") int memoryMaxEntries,
        @DefaultValue("5") int rateLimitPerDestination,
        @DefaultValue("20") int rateLimitPerIp,
        @DefaultValue("600") int rateLimitWindowSeconds,
        @DefaultValue("600") int lockSeconds,
        @DefaultValue("10") int verifyRateLimitPerDestination,
        @DefaultValue("30") int verifyRateLimitPerIp,
        @DefaultValue("10") int connectRateLimitPerIp,
        @DefaultValue("0") int dailySendLimit,
        @DefaultValue("false") boolean logCodes,
        @DefaultValue("false") boolean customMessageEnabled
) {
    public static final String INSECURE_DEV_SECRET = "dev-only-secret-change-me";

    public OtpSettings settings() {
        return new OtpSettings(digits, durationSeconds, maxAttempts, retentionSeconds, lockSeconds, demoMode,
                customMessageEnabled);
    }

    // El record imprimiria hashSecret en claro: un toString, un log de depuracion o un volcado de configuracion lo filtraria.
    @Override
    public String toString() {
        return "OtpProperties[hashSecret=oculto, demoMode=" + demoMode + ", logCodes=" + logCodes
                + ", customMessageEnabled=" + customMessageEnabled + ", dailySendLimit=" + dailySendLimit + "]";
    }

    public boolean usingInsecureDevSecret() {
        return INSECURE_DEV_SECRET.equals(hashSecret);
    }
}
