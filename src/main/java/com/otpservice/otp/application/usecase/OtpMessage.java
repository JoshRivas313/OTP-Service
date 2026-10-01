package com.otpservice.otp.application.usecase;

import com.otpservice.otp.application.protocol.IssuedCode;

// Texto que recibe el usuario. Un codigo sin vencimiento (HOTP) no dice "vence en": dice que sirve hasta usarlo.
final class OtpMessage {

    private static final String NO_EXPIRY_TEMPLATE = "Tu código de verificación es %s. Sirve hasta que lo uses.";
    private static final String EXPIRY_CLAUSE = "Vence en {seconds} segundos.";
    private static final String NO_EXPIRY_CLAUSE = "Sirve hasta que lo uses.";

    private OtpMessage() {
    }

    static String build(String customMessage, boolean expires, IssuedCode issued, String defaultTemplate) {
        if (customMessage == null || customMessage.isBlank()) {
            return expires
                    ? defaultTemplate.formatted(issued.code(), issued.expiresInSeconds())
                    : NO_EXPIRY_TEMPLATE.formatted(issued.code());
        }
        String message = customMessage.replace("{code}", issued.code());
        return expires
                ? message.replace("{seconds}", String.valueOf(issued.expiresInSeconds()))
                : message.replace(EXPIRY_CLAUSE, NO_EXPIRY_CLAUSE).replace("{seconds}", "");
    }
}
