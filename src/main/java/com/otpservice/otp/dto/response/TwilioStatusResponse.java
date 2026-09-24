package com.otpservice.otp.dto.response;

public record TwilioStatusResponse(boolean connected, String maskedCredentials) {

    public static TwilioStatusResponse connected(String maskedCredentials) {
        return new TwilioStatusResponse(true, maskedCredentials);
    }

    public static TwilioStatusResponse disconnected() {
        return new TwilioStatusResponse(false, null);
    }
}
