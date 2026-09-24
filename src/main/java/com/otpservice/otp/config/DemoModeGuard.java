package com.otpservice.otp.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * otp.demo-mode expone el codigo en la respuesta de la API. Si eso coincide
 * con un proveedor de SMS real (Twilio/Infobip), el codigo saldria dos veces
 * por dos canales distintos, uno de ellos gratis para cualquiera que llame
 * al endpoint. Se corta al arrancar para que nunca quede activo por error.
 */
@Component
@RequiredArgsConstructor
public class DemoModeGuard {

    private static final Set<String> REAL_PROVIDERS = Set.of("twilio", "infobip");

    private final OtpProperties otpProperties;
    private final SmsProperties smsProperties;

    @PostConstruct
    void checkNotCombinedWithRealProvider() {
        if (otpProperties.demoMode() && REAL_PROVIDERS.contains(smsProperties.provider())) {
            throw new IllegalStateException(
                    "otp.demo-mode no puede estar activo junto a sms.provider=" + smsProperties.provider());
        }
    }
}
