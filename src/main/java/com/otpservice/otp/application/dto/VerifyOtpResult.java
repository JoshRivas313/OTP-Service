package com.otpservice.otp.application.dto;


public record VerifyOtpResult(boolean success, String message) {

    public static VerifyOtpResult verified() {
        return new VerifyOtpResult(true, "Código verificado correctamente");
    }
}
