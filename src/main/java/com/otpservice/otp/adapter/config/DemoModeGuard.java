package com.otpservice.otp.adapter.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DemoModeGuard {

    private final OtpProperties otpProperties;
    private final SmsProperties smsProperties;
    private final EmailProperties emailProperties;

    @PostConstruct
    void checkNotCombinedWithRealProvider() {
        if (!otpProperties.demoMode()) {
            return;
        }
        if (smsProperties.provider().isReal()) {
            throw new IllegalStateException(
                    "otp.demo-mode no puede estar activo junto a sms.provider=" + smsProperties.provider().value());
        }
        if (emailProperties.provider().isReal()) {
            throw new IllegalStateException(
                    "otp.demo-mode no puede estar activo junto a email.provider=" + emailProperties.provider().value());
        }
    }
}
