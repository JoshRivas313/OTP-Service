package com.otpservice.otp.exception;

import com.otpservice.otp.dto.response.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(OtpException.class)
    public ResponseEntity<ErrorResponse> handleOtpException(OtpException exception) {
        ErrorCode code = exception.getErrorCode();
        return ResponseEntity.status(code.getStatus())
                .body(ErrorResponse.of(code.name(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse(ErrorCode.VALIDATION_ERROR.getDefaultMessage());
        return ResponseEntity.status(ErrorCode.VALIDATION_ERROR.getStatus())
                .body(ErrorResponse.of(ErrorCode.VALIDATION_ERROR.name(), message));
    }
}
