package com.otpservice.otp.application.dto;

import com.otpservice.otp.domain.valueobject.OtpProtocol;

public record VerifyOtpResult(boolean success, String message, OtpProtocol protocol, Long counter, Long timeStep) {

    public static VerifyOtpResult verified() {
        return new VerifyOtpResult(true, "Código verificado correctamente", OtpProtocol.OTP, null, null);
    }

    public static VerifyOtpResult verified(OtpProtocol protocol, Long counter, Long timeStep) {
        return new VerifyOtpResult(true, "Código verificado correctamente", protocol, counter, timeStep);
    }
}
