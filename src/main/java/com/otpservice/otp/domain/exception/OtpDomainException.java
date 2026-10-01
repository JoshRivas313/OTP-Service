package com.otpservice.otp.domain.exception;

public abstract class OtpDomainException extends RuntimeException {

    private final ErrorCode errorCode;

    protected OtpDomainException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
