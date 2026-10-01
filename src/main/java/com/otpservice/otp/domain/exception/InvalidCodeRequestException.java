package com.otpservice.otp.domain.exception;

public class InvalidCodeRequestException extends OtpDomainException {
    public InvalidCodeRequestException(String message) {
        super(ErrorCode.OTP_INVALID_REQUEST, message);
    }
}
