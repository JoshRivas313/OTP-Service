package com.otpservice.otp.adapter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "email")
public record EmailProperties(
        @DefaultValue("console") EmailProvider provider,
        @DefaultValue("Un Solo Uso") String senderName,
        @DefaultValue("") String senderAddress,
        @DefaultValue Brevo brevo
) {
    public record Brevo(
            @DefaultValue("") String apiKey,
            @DefaultValue("https://api.brevo.com") String baseUrl
    ) {
        @Override
        public String toString() {
            return "Brevo[apiKey=" + (apiKey.isBlank() ? "vacia" : "oculta") + ", baseUrl=" + baseUrl + "]";
        }
    }
}
