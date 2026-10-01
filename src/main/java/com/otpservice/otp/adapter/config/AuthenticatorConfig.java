package com.otpservice.otp.adapter.config;

import com.otpservice.otp.application.config.AuthenticatorSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AuthenticatorConfig {

    @Bean
    public AuthenticatorSettings authenticatorSettings(AuthenticatorProperties properties) {
        return new AuthenticatorSettings(
                properties.issuer(),
                properties.totpToleranceSteps(),
                properties.hotpLookAhead(),
                properties.maxFailedAttempts(),
                properties.lockSeconds());
    }
}
