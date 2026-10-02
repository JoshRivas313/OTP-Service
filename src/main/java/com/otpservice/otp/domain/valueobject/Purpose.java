package com.otpservice.otp.domain.valueobject;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

// Para qué se pidió el código. Es parte de su identidad: un código de un propósito no sirve para otro.
public enum Purpose {

    LOGIN,
    REGISTER,
    PASSWORD_RECOVERY,
    PAYMENT_CONFIRMATION;

    @JsonCreator
    public static Purpose from(String value) {
        if (value == null || value.isBlank()) {
            return LOGIN;
        }
        return Purpose.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
