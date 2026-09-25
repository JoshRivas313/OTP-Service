package com.otpservice.otp.domain.exception;

/**
 * Base type for OTP business-rule violations. Deliberately carries no
 * HTTP status — that mapping is an HTTP-adapter concern
 * (see adapter/in/http/GlobalExceptionHandler). Only a stable string
 * code the client can match on, plus a human-readable message.
 */
public abstract class OtpDomainException extends RuntimeException {

    private final String errorCode;

    protected OtpDomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
