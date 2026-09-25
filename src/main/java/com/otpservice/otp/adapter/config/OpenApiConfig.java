package com.otpservice.otp.adapter.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI otpServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("OTP Service")
                .description("Autenticación por código de un solo uso, con generación local (Mongo + HMAC-SHA256) o via Twilio Verify.")
                .version("v0.0.1"));
    }
}
