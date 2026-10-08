package com.otpservice.otp.adapter.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "hmac")
public record HmacProperties(
        @DefaultValue("1") @Min(0) int totpToleranceSteps,
        @DefaultValue("10") @Min(1) int hotpLookAhead,
        String encryptionKey
) {
    public static final String INSECURE_DEV_KEY = "ZGV2LW9ubHktZW5jcnlwdGlvbi1rZXktY2hhbmdlbWU=";

    @Override
    public String toString() {
        return "HmacProperties[totpToleranceSteps=" + totpToleranceSteps + ", hotpLookAhead=" + hotpLookAhead
                + ", encryptionKey=oculta]";
    }

    public boolean usingInsecureDevKey() {
        return INSECURE_DEV_KEY.equals(encryptionKey);
    }
}
