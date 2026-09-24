package com.otpservice.otp.dto.response;

public record OtpGenerateResponse(boolean success, String message, String demoCode) {

    public static OtpGenerateResponse sent() {
        return new OtpGenerateResponse(true, "Código enviado correctamente", null);
    }

    public static OtpGenerateResponse sentInDemoMode(String code) {
        return new OtpGenerateResponse(true,
                "Código enviado (modo demo, no llega SMS real)", code);
    }
}
