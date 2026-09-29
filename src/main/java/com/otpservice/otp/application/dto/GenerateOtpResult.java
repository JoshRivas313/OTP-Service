package com.otpservice.otp.application.dto;

/**
 * Outcome of GenerateOtpUseCase, owned by the application layer.
 * Deliberately distinct from adapter/in/http/dto/response/OtpGenerateResponse:
 * the use case must not depend on the HTTP response shape.
 */
public record GenerateOtpResult(boolean success, String message, String demoCode) {

    public static GenerateOtpResult sent() {
        return new GenerateOtpResult(true, "Código enviado correctamente", null);
    }

    public static GenerateOtpResult sentInDemoMode(String code) {
        return new GenerateOtpResult(true,
                "Código enviado (modo demo, no llega SMS real)", code);
    }
}
