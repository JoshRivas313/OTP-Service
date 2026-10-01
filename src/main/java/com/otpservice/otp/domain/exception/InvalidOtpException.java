package com.otpservice.otp.domain.exception;

public class InvalidOtpException extends OtpDomainException {
    public InvalidOtpException() {
        this("El código es incorrecto");
    }

    public static InvalidOtpException attempt(int attempts, int maxAttempts) {
        return new InvalidOtpException("El código es incorrecto (intento %d de %d)".formatted(attempts, maxAttempts));
    }

    public InvalidOtpException(String message) {
        super(ErrorCode.OTP_INVALID, message);
    }
}
