package com.otpservice.otp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "sms")
public record SmsProperties(
        @DefaultValue("console") String provider,
        @DefaultValue Twilio twilio,
        @DefaultValue Infobip infobip
) {
    public record Twilio(
            @DefaultValue("") String accountSid,
            @DefaultValue("") String authToken,
            @DefaultValue("") String phoneNumber
    ) {
    }

    public record Infobip(
            @DefaultValue("") String baseUrl,
            @DefaultValue("") String apiKey,
            @DefaultValue("") String sender
    ) {
    }
}
