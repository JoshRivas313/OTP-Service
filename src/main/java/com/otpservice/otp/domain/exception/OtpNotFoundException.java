package com.otpservice.otp.domain.exception;

public class OtpNotFoundException extends OtpDomainException {
    public OtpNotFoundException() {
        super("OTP_NOT_FOUND", "No existe un código para este número");
    }
}
