package com.otpservice.otp.adapter.in.http;

import com.otpservice.otp.adapter.in.http.dto.response.ErrorResponse;
import com.otpservice.otp.domain.exception.InvalidOtpException;
import com.otpservice.otp.domain.exception.OtpAlreadyUsedException;
import com.otpservice.otp.domain.exception.OtpBlockedException;
import com.otpservice.otp.domain.exception.OtpDomainException;
import com.otpservice.otp.domain.exception.OtpExpiredException;
import com.otpservice.otp.domain.exception.OtpInvalidatedException;
import com.otpservice.otp.domain.exception.OtpNotFoundException;
import com.otpservice.otp.domain.exception.SmsDeliveryFailedException;
import com.otpservice.otp.domain.exception.TwilioCredentialsInvalidException;
import com.otpservice.otp.domain.exception.TwilioNotConnectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Domain exceptions (domain/exception/*) carry no HTTP knowledge on purpose;
 * this is the one place that maps a business-rule violation to a status
 * code, keeping that mapping out of the domain and application layers.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OtpDomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(OtpDomainException exception) {
        HttpStatus status = statusFor(exception);
        return ResponseEntity.status(status)
                .body(ErrorResponse.of(exception.getErrorCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Solicitud inválida");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", message));
    }

    private HttpStatus statusFor(OtpDomainException exception) {
        return switch (exception) {
            case OtpNotFoundException e -> HttpStatus.NOT_FOUND;
            case OtpInvalidatedException e -> HttpStatus.CONFLICT;
            case OtpAlreadyUsedException e -> HttpStatus.CONFLICT;
            case OtpExpiredException e -> HttpStatus.GONE;
            case OtpBlockedException e -> HttpStatus.LOCKED;
            case InvalidOtpException e -> HttpStatus.UNAUTHORIZED;
            case SmsDeliveryFailedException e -> HttpStatus.BAD_GATEWAY;
            case TwilioCredentialsInvalidException e -> HttpStatus.UNAUTHORIZED;
            case TwilioNotConnectedException e -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
