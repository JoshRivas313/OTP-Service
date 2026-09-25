package com.otpservice.otp.shared.exception;

public class OtpException extends RuntimeException {

    private final ErrorCode errorCode;

    public OtpException(ErrorCode errorCode) {
        this(errorCode, errorCode.getDefaultMessage());
    }

    public OtpException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
