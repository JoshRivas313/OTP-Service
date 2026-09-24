package com.otpservice.otp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "twilio-connect")
public record TwilioConnectProperties(
        @DefaultValue("false") boolean enabled
) {
}
