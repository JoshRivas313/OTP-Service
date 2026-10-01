package com.otpservice.otp.application.config;

public record HmacSettings(int totpToleranceSteps, int hotpLookAhead) {
}
