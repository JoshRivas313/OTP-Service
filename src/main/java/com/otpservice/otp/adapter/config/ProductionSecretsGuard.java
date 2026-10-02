package com.otpservice.otp.adapter.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

// En produccion la aplicacion no arranca con claves ausentes o con las de desarrollo, que estan en el repositorio.
@Component
@Profile("prod")
@RequiredArgsConstructor
public class ProductionSecretsGuard {

    private final OtpProperties otpProperties;
    private final HmacProperties hmacProperties;

    @PostConstruct
    void checkSecrets() {
        String hashSecret = otpProperties.hashSecret();
        if (hashSecret == null || hashSecret.isBlank() || otpProperties.usingInsecureDevSecret()) {
            throw new IllegalStateException(
                    "Perfil prod: defina OTP_HASH_SECRET con un valor propio, largo y aleatorio");
        }
        String encryptionKey = hmacProperties.encryptionKey();
        if (encryptionKey == null || encryptionKey.isBlank() || hmacProperties.usingInsecureDevKey()) {
            throw new IllegalStateException(
                    "Perfil prod: defina OTP_SECRET_ENCRYPTION_KEY con 32 bytes en Base64 (openssl rand -base64 32)");
        }
    }
}
