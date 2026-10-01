package com.otpservice.otp.adapter.config;

import com.otpservice.otp.application.config.OtpSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OtpConfig {

    @Bean
    public OtpSettings otpSettings(OtpProperties properties) {
        return properties.settings();
    }
}
