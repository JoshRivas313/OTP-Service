package com.otpservice.otp.dto.response;

public record OtpGenerateResponse(boolean success, String message) {

    public static OtpGenerateResponse sent() {
        return new OtpGenerateResponse(true, "Código enviado correctamente");
    }
}
