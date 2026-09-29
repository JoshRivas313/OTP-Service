package com.otpservice.otp.adapter.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

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
