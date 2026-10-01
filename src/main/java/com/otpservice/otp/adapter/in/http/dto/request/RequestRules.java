package com.otpservice.otp.adapter.in.http.dto.request;

import com.otpservice.otp.domain.valueobject.OtpCode;
import com.otpservice.otp.domain.valueobject.ValidityWindow;

// Limites compartidos por las peticiones de generar un codigo.
final class RequestRules {

    static final String DIGITS_MIN_MESSAGE = "Mínimo " + OtpCode.MIN_LENGTH + " dígitos";
    static final String DIGITS_MAX_MESSAGE = "Máximo " + OtpCode.MAX_LENGTH + " dígitos";
    static final String DURATION_POSITIVE_MESSAGE = "La duración debe ser mayor a 0 segundos";
    static final String DURATION_MAX_MESSAGE = "La duración no puede superar un día";

    static final int MAX_MESSAGE_LENGTH = 300;
    static final String MESSAGE_LENGTH_MESSAGE = "El mensaje no puede superar " + MAX_MESSAGE_LENGTH + " caracteres";
    static final String MESSAGE_PATTERN = "(?s).*\\{code}.*";
    static final String MESSAGE_PATTERN_MESSAGE = "El mensaje debe incluir {code}";

    static final int MAX_DURATION_SECONDS = ValidityWindow.MAX_DURATION_SECONDS;

    private RequestRules() {
    }
}
