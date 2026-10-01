package com.otpservice.otp.adapter.in.http;

import com.otpservice.otp.adapter.in.http.dto.response.ErrorResponse;
import com.otpservice.otp.domain.exception.ErrorCode;
import com.otpservice.otp.domain.exception.OtpDomainException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OtpDomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(OtpDomainException exception) {
        return ResponseEntity.status(statusFor(exception.errorCode()))
                .body(ErrorResponse.of(exception.errorCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Solicitud inválida");
        return ResponseEntity.status(statusFor(ErrorCode.VALIDATION_ERROR))
                .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR, message));
    }

    // Sin "default": si se agrega un ErrorCode y no se mapea aqui, el compilador lo senala.
    private static HttpStatus statusFor(ErrorCode code) {
        return switch (code) {
            case OTP_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case OTP_INVALIDATED, OTP_ALREADY_USED -> HttpStatus.CONFLICT;
            case OTP_EXPIRED -> HttpStatus.GONE;
            case OTP_BLOCKED -> HttpStatus.LOCKED;
            case OTP_INVALID, TWILIO_CREDENTIALS_INVALID -> HttpStatus.UNAUTHORIZED;
            case OTP_INVALID_REQUEST, TWILIO_NOT_CONNECTED, VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            case SMS_DELIVERY_FAILED, EMAIL_DELIVERY_FAILED -> HttpStatus.BAD_GATEWAY;
            case DESTINATION_NOT_VERIFIED -> HttpStatus.FORBIDDEN;
            case RATE_LIMIT_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
        };
    }
}
