package com.otpservice.otp.domain.exception;

public class InvalidOtpException extends OtpDomainException {
    public InvalidOtpException() {
        this("El código es incorrecto");
    }

    public InvalidOtpException(String message) {
        super("OTP_INVALID", message);
    }
}
