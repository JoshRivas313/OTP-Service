package com.otpservice.otp.adapter.in.http.dto.response;

public record OtpVerifyResponse(boolean success, String message) {

    public static OtpVerifyResponse verified() {
        return new OtpVerifyResponse(true, "Código verificado correctamente");
    }
}
