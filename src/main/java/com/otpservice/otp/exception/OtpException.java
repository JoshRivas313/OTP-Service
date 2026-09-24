package com.otpservice.otp.exception;

public class OtpException extends RuntimeException {

    private final String code;

    public OtpException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
