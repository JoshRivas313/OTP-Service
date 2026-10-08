package com.otpservice.otp.adapter.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

// La aplicacion no arranca sin claves propias: application.yaml no trae ninguna. Los valores de ejemplo viven solo en
// application-dev.yaml, que hay que activar a proposito (SPRING_PROFILES_ACTIVE=dev) y que no puede convivir con prod.
@Component
@RequiredArgsConstructor
public class RequiredSecretsGuard {

    private static final String HOW_TO_RUN_LOCALLY = "Para probar en local activa el perfil dev: SPRING_PROFILES_ACTIVE=dev";

    private final OtpProperties otpProperties;
    private final HmacProperties hmacProperties;
    private final Environment environment;

    @PostConstruct
    void checkSecrets() {
        boolean dev = environment.acceptsProfiles(Profiles.of("dev"));
        if (dev && environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("El perfil dev no puede combinarse con prod: trae claves de ejemplo públicas");
        }
        if (isMissing(otpProperties.hashSecret()) || (!dev && otpProperties.usingInsecureDevSecret())) {
            throw new IllegalStateException(
                    "Falta OTP_HASH_SECRET: define un valor propio, largo y aleatorio. " + HOW_TO_RUN_LOCALLY);
        }
        if (isMissing(hmacProperties.encryptionKey()) || (!dev && hmacProperties.usingInsecureDevKey())) {
            throw new IllegalStateException("Falta OTP_SECRET_ENCRYPTION_KEY: define 32 bytes en Base64 "
                    + "(openssl rand -base64 32). " + HOW_TO_RUN_LOCALLY);
        }
    }

    private static boolean isMissing(String value) {
        return value == null || value.isBlank();
    }
}
