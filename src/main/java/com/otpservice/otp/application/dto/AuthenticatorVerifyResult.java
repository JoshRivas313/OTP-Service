package com.otpservice.otp.application.dto;

import com.otpservice.otp.domain.valueobject.HmacType;

public record AuthenticatorVerifyResult(boolean success, String message, HmacType type,
                                        Long timeStep, Long counter) {

    public static AuthenticatorVerifyResult of(String message, HmacType type, long matchedValue) {
        return type == HmacType.TOTP
                ? new AuthenticatorVerifyResult(true, message, type, matchedValue, null)
                : new AuthenticatorVerifyResult(true, message, type, null, matchedValue);
    }
}
