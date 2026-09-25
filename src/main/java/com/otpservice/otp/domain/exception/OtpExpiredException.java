package com.otpservice.otp.domain.exception;

public class OtpExpiredException extends OtpDomainException {
    public OtpExpiredException() {
        super("OTP_EXPIRED", "El código ha expirado");
    }
}
