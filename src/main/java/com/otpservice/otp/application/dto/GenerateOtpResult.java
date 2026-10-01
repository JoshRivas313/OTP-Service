package com.otpservice.otp.application.dto;

import com.otpservice.otp.domain.valueobject.OtpProtocol;

public record GenerateOtpResult(boolean success, String message, String demoCode, OtpProtocol protocol,
                                Long expiresInSeconds, Long counter, Long timeStep) {

    public static GenerateOtpResult sent(OtpProtocol protocol, Long expiresInSeconds, Long counter, Long timeStep) {
        return new GenerateOtpResult(true, "Código enviado correctamente", null,
                protocol, expiresInSeconds, counter, timeStep);
    }

    public static GenerateOtpResult sentInDemoMode(String code, OtpProtocol protocol, Long expiresInSeconds,
                                                   Long counter, Long timeStep) {
        return new GenerateOtpResult(true, "Código generado (modo demo, no se envía por ningún canal)", code,
                protocol, expiresInSeconds, counter, timeStep);
    }
}
