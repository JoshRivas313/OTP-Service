package com.otpservice.otp.application.usecase;

import com.otpservice.otp.application.protocol.IssuedCode;
import com.otpservice.otp.domain.valueobject.Purpose;

// Texto que recibe el usuario. Lo decide el servidor a partir del proposito: quien pide el codigo no escribe el texto,
// salvo que la configuracion lo permita (otp.custom-message-enabled). Un codigo sin vencimiento (HOTP) no dice "vence en":
// dice que sirve hasta usarlo.
final class OtpMessage {

    private static final String EXPIRY_CLAUSE = "Vence en {seconds} segundos.";
    private static final String NO_EXPIRY_CLAUSE = "Sirve hasta que lo uses.";

    private OtpMessage() {
    }

    static String build(String customMessage, boolean expires, IssuedCode issued, Purpose purpose) {
        if (customMessage == null || customMessage.isBlank()) {
            return standard(purpose, expires, issued);
        }
        String message = customMessage.replace("{code}", issued.code());
        return expires
                ? message.replace("{seconds}", String.valueOf(issued.expiresInSeconds()))
                : message.replace(EXPIRY_CLAUSE, NO_EXPIRY_CLAUSE).replace("{seconds}", "");
    }

    // TOTP: los segundos dependen de en que momento de la ventana se pidio el codigo.
    private static String standard(Purpose purpose, boolean expires, IssuedCode issued) {
        String lead = "Tu código " + reason(purpose) + " es " + issued.code() + ". ";
        if (!expires) {
            return lead + NO_EXPIRY_CLAUSE;
        }
        String clause = "Vence en " + issued.expiresInSeconds() + " segundos";
        return lead + (issued.timeStep() != null ? clause + ", al cerrar su ventana." : clause + ".");
    }

    private static String reason(Purpose purpose) {
        return switch (purpose) {
            case LOGIN -> "para iniciar sesión";
            case REGISTER -> "para crear tu cuenta";
            case PASSWORD_RECOVERY -> "para recuperar tu acceso";
            case PAYMENT_CONFIRMATION -> "para confirmar tu pago";
        };
    }
}
