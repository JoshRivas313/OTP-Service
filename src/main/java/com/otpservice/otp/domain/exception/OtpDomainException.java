package com.otpservice.otp.domain.exception;

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
