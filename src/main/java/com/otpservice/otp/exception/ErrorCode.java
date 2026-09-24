package com.otpservice.otp.exception;

import org.springframework.http.HttpStatus;

/**
 * Catalogo unico de errores de negocio. Cada valor fija el codigo estable
 * que ve el cliente, el mensaje por defecto y el estado HTTP, para que la
 * respuesta nunca dependa de un string suelto escrito a mano en cada throw.
 */
public enum ErrorCode {

    OTP_NOT_FOUND(HttpStatus.NOT_FOUND, "No existe un código para este número"),
    OTP_INVALIDATED(HttpStatus.CONFLICT, "El código fue reemplazado por uno más reciente"),
    OTP_ALREADY_USED(HttpStatus.CONFLICT, "El código ya fue utilizado"),
    OTP_EXPIRED(HttpStatus.GONE, "El código ha expirado"),
    OTP_BLOCKED(HttpStatus.LOCKED, "El código fue bloqueado por demasiados intentos fallidos"),
    OTP_INVALID(HttpStatus.UNAUTHORIZED, "El código es incorrecto"),
    SMS_DELIVERY_FAILED(HttpStatus.BAD_GATEWAY, "No se pudo enviar el SMS"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Solicitud inválida");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
