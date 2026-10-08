package com.otpservice.otp.adapter.in.http;

import com.otpservice.otp.adapter.in.http.dto.response.ErrorResponse;
import com.otpservice.otp.domain.exception.ErrorCode;
import com.otpservice.otp.domain.exception.OtpDomainException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

// Una sola forma de error para toda la API: {success, code, message}. Extiende ResponseEntityExceptionHandler para que
// tambien los errores del framework (ruta inexistente, metodo o formato no soportado, cuerpo ilegible) salgan asi.
// Nunca se devuelve el mensaje de una excepcion inesperada: va al log, no al cliente.
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final String UNREADABLE_BODY = "Solicitud inválida: revisa los valores enviados";

    @ExceptionHandler(OtpDomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(OtpDomainException exception) {
        return ResponseEntity.status(statusFor(exception.errorCode()))
                .body(ErrorResponse.of(exception.errorCode(), exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        log.error("Error no controlado", exception);
        return ResponseEntity.status(statusFor(ErrorCode.INTERNAL_ERROR))
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, messageFor(ErrorCode.INTERNAL_ERROR)));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Solicitud inválida");
        return respond(ErrorCode.VALIDATION_ERROR, message, headers);
    }

    // Cuerpo ilegible o con un valor fuera de catalogo (por ejemplo un purpose que no existe).
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        return respond(ErrorCode.VALIDATION_ERROR, UNREADABLE_BODY, headers);
    }

    // Todos los demas errores del framework: el estado lo fija Spring, el cuerpo es el nuestro.
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders headers,
                                                             HttpStatusCode status, WebRequest request) {
        ErrorCode code = codeFor(status);
        if (status.is5xxServerError()) {
            log.error("Error del framework", exception);
        }
        return ResponseEntity.status(status).headers(headers).body(ErrorResponse.of(code, messageFor(code)));
    }

    private ResponseEntity<Object> respond(ErrorCode code, String message, HttpHeaders headers) {
        return ResponseEntity.status(statusFor(code)).headers(headers).body(ErrorResponse.of(code, message));
    }

    private static ErrorCode codeFor(HttpStatusCode status) {
        if (status.is5xxServerError()) {
            return ErrorCode.INTERNAL_ERROR;
        }
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return ErrorCode.RESOURCE_NOT_FOUND;
        }
        if (status.value() == HttpStatus.METHOD_NOT_ALLOWED.value()) {
            return ErrorCode.METHOD_NOT_ALLOWED;
        }
        if (status.value() == HttpStatus.UNSUPPORTED_MEDIA_TYPE.value()) {
            return ErrorCode.UNSUPPORTED_MEDIA_TYPE;
        }
        return ErrorCode.VALIDATION_ERROR;
    }

    private static String messageFor(ErrorCode code) {
        return switch (code) {
            case RESOURCE_NOT_FOUND -> "No existe ese recurso";
            case METHOD_NOT_ALLOWED -> "Ese método no está permitido en esta ruta";
            case UNSUPPORTED_MEDIA_TYPE -> "Formato no soportado: envía application/json";
            case INTERNAL_ERROR -> "Ocurrió un error inesperado. Intenta de nuevo en un momento";
            default -> UNREADABLE_BODY;
        };
    }

    // Sin "default": si se agrega un ErrorCode y no se mapea aqui, el compilador lo senala.
    private static HttpStatus statusFor(ErrorCode code) {
        return switch (code) {
            case OTP_NOT_FOUND, RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case OTP_INVALIDATED, OTP_ALREADY_USED -> HttpStatus.CONFLICT;
            case OTP_EXPIRED -> HttpStatus.GONE;
            case OTP_BLOCKED -> HttpStatus.LOCKED;
            case OTP_INVALID, TWILIO_CREDENTIALS_INVALID -> HttpStatus.UNAUTHORIZED;
            case OTP_INVALID_REQUEST, TWILIO_NOT_CONNECTED, VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            case SMS_DELIVERY_FAILED, EMAIL_DELIVERY_FAILED -> HttpStatus.BAD_GATEWAY;
            case DESTINATION_NOT_VERIFIED -> HttpStatus.FORBIDDEN;
            case RATE_LIMIT_EXCEEDED -> HttpStatus.TOO_MANY_REQUESTS;
            case DAILY_QUOTA_EXCEEDED -> HttpStatus.SERVICE_UNAVAILABLE;
            case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
            case UNSUPPORTED_MEDIA_TYPE -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case PAYLOAD_TOO_LARGE -> HttpStatus.valueOf(413);
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
