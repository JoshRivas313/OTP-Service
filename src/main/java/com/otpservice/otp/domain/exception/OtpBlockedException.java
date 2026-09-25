package com.otpservice.otp.domain.exception;

public class OtpBlockedException extends OtpDomainException {
    public OtpBlockedException() {
        super("OTP_BLOCKED", "El código fue bloqueado por demasiados intentos fallidos");
    }
}
