package com.otpservice.otp.adapter.config;

import com.otpservice.otp.application.config.HmacSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HmacConfig {

    @Bean
    public HmacSettings hmacSettings(HmacProperties properties) {
        return new HmacSettings(properties.totpToleranceSteps(), properties.hotpLookAhead());
    }
}
