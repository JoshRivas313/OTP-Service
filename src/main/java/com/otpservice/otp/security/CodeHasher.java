package com.otpservice.otp.security;

import com.otpservice.otp.config.OtpProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

@Slf4j
@Component
@RequiredArgsConstructor
public class CodeHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final OtpProperties properties;

    @PostConstruct
    void warnAboutInsecureSecret() {
        if (properties.usingInsecureDevSecret()) {
            log.warn("otp.hash-secret usa el valor de desarrollo por defecto");
        }
    }

    public String hash(String plainCode) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(properties.hashSecret().getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(plainCode.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo calcular el hash del código", exception);
        }
    }
}
