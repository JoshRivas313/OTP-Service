package com.otpservice.otp.adapter.exception;

import com.otpservice.otp.domain.exception.ErrorCode;
import com.otpservice.otp.domain.exception.OtpDomainException;

public class RateLimitExceededException extends OtpDomainException {

    private RateLimitExceededException(String message) {
        super(ErrorCode.RATE_LIMIT_EXCEEDED, message);
    }

    public static RateLimitExceededException sending() {
        return new RateLimitExceededException("Demasiados envíos seguidos. Prueba de nuevo en unos minutos");
    }

    public static RateLimitExceededException verifying() {
        return new RateLimitExceededException("Demasiados intentos de verificación. Prueba de nuevo en unos minutos");
    }
}
