package com.otpservice.otp.adapter.config;

import java.util.Locale;

public enum SmsProvider {

    CONSOLE,
    TWILIO,
    INFOBIP;

    public boolean isReal() {
        return this != CONSOLE;
    }

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }
}
