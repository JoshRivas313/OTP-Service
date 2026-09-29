package com.otpservice.otp.adapter.out.sms.twilio;

import com.twilio.exception.ApiException;
import com.twilio.exception.TwilioException;

public final class TwilioErrorMessages {

    private TwilioErrorMessages() {
    }

    public static Integer codeOf(TwilioException exception) {
        return exception instanceof ApiException apiException ? apiException.getCode() : null;
    }

    public static String describe(Integer code) {
        if (code == null) {
            return "No se pudo enviar el SMS";
        }
        return switch (code) {
            case 21608 -> "Tu cuenta de Twilio es de prueba y ese número no está verificado. Verificalo en la consola de Twilio";
            case 21211, 21614 -> "Twilio no reconoce ese número como un celular válido";
            case 21408 -> "Tu cuenta de Twilio no tiene permiso para enviar SMS a Perú. Activalo en Geo Permissions";
            case 21606, 21212, 21659 -> "El número de Twilio configurado no puede enviar SMS. Revisá que sea tuyo y tenga SMS habilitado";
            case 21610 -> "Ese número pidió no recibir mensajes de tu número de Twilio";
            case 20003 -> "Twilio rechazó las credenciales de la sesión. Volvé a conectar tu cuenta";
            default -> "No se pudo enviar el SMS (código de Twilio " + code + ")";
        };
    }
}
