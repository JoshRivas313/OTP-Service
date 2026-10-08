package com.otpservice.otp.adapter.exception;

import com.otpservice.otp.domain.exception.ErrorCode;
import com.otpservice.otp.domain.exception.OtpDomainException;

public class RateLimitExceededException extends OtpDomainException {

    private RateLimitExceededException(ErrorCode code, String message) {
        super(code, message);
    }

    public static RateLimitExceededException sending() {
        return new RateLimitExceededException(ErrorCode.RATE_LIMIT_EXCEEDED,
                "Demasiados envíos seguidos. Prueba de nuevo en unos minutos");
    }

    public static RateLimitExceededException verifying() {
        return new RateLimitExceededException(ErrorCode.RATE_LIMIT_EXCEEDED,
                "Demasiados intentos de verificación. Prueba de nuevo en unos minutos");
    }

    public static RateLimitExceededException connecting() {
        return new RateLimitExceededException(ErrorCode.RATE_LIMIT_EXCEEDED,
                "Demasiados intentos de conexión. Prueba de nuevo en unos minutos");
    }

    // No es culpa de quien pide: se agoto la cuota diaria del servicio, por eso responde 503 y no 429.
    public static RateLimitExceededException dailyQuota() {
        return new RateLimitExceededException(ErrorCode.DAILY_QUOTA_EXCEEDED,
                "La demo agotó sus envíos de hoy. Vuelve mañana o mira el tablero de la portada, que no necesita enviar nada");
    }
}
