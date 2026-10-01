package com.otpservice.otp.adapter.exception;

import com.otpservice.otp.domain.exception.OtpDomainException;

public class RateLimitExceededException extends OtpDomainException {
    public RateLimitExceededException() {
        super("RATE_LIMIT_EXCEEDED", "Demasiados envíos seguidos. Probá de nuevo en unos minutos");
    }
}
