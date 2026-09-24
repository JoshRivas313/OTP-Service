package com.otpservice.otp.dto.response;

public record OtpVerifyResponse(boolean success, String message) {

    public static OtpVerifyResponse verified() {
        return new OtpVerifyResponse(true, "Código verificado correctamente");
    }
}
