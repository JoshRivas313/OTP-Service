package com.otpservice.otp.domain.exception;

// El nombre de cada valor es el codigo estable que ve el cliente de la API.
public enum ErrorCode {

    OTP_NOT_FOUND,
    OTP_INVALIDATED,
    OTP_ALREADY_USED,
    OTP_EXPIRED,
    OTP_BLOCKED,
    OTP_INVALID,
    OTP_INVALID_REQUEST,
    SMS_DELIVERY_FAILED,
    EMAIL_DELIVERY_FAILED,
    TWILIO_CREDENTIALS_INVALID,
    TWILIO_NOT_CONNECTED,
    DESTINATION_NOT_VERIFIED,
    RATE_LIMIT_EXCEEDED,
    VALIDATION_ERROR
}
