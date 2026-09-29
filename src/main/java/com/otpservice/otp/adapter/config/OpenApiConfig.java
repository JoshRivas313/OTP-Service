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
                .description("Generación y verificación de códigos de un solo uso (OTP) con MongoDB y HMAC-SHA256, y envío por SMS con consola, Twilio o Infobip.")
                .version("v0.0.1"));
    }
}
