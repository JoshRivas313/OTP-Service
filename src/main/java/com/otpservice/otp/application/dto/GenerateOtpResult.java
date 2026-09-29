package com.otpservice.otp.application.dto;


public record GenerateOtpResult(boolean success, String message, String demoCode) {

    public static GenerateOtpResult sent() {
        return new GenerateOtpResult(true, "Código enviado correctamente", null);
    }

    public static GenerateOtpResult sentInDemoMode(String code) {
        return new GenerateOtpResult(true,
                "Código enviado (modo demo, no llega SMS real)", code);
    }
}
