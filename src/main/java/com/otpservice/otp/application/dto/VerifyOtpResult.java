package com.otpservice.otp.application.dto;

/**
 * Outcome of VerifyOtpUseCase, owned by the application layer.
 * Deliberately distinct from adapter/in/http/dto/response/OtpVerifyResponse.
 */
public record VerifyOtpResult(boolean success, String message) {

    public static VerifyOtpResult verified() {
        return new VerifyOtpResult(true, "Código verificado correctamente");
    }
}
