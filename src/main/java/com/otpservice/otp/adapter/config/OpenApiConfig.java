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
                .title("Un Solo Uso")
                .description("Códigos de un solo uso por SMS o correo, con tres protocolos: OTP aleatorio, HOTP (RFC 4226) y TOTP (RFC 6238).")
                .version("v0.0.1"));
    }
}
