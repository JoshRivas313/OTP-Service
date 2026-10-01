package com.otpservice.otp.domain.valueobject;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum OtpProtocol {

    OTP,
    HOTP,
    TOTP;

    @JsonCreator
    public static OtpProtocol from(String value) {
        if (value == null || value.isBlank()) {
            return OTP;
        }
        return OtpProtocol.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

}
