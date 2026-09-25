package com.otpservice.otp.domain.exception;

public class OtpAlreadyUsedException extends OtpDomainException {
    public OtpAlreadyUsedException() {
        super("OTP_ALREADY_USED", "El código ya fue utilizado");
    }
}
