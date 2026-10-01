package com.otpservice.otp.domain.valueobject;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

public enum HmacType {

    HOTP,
    TOTP;

    // HOTP no tiene ventana de tiempo.
    public int effectivePeriod(int periodSeconds) {
        return switch (this) {
            case HOTP -> 0;
            case TOTP -> periodSeconds;
        };
    }

    @JsonCreator
    public static HmacType from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("El tipo es obligatorio: HOTP o TOTP");
        }
        return HmacType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }

}
