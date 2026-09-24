package com.otpservice.otp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// Prefijo separado de sms.twilio: eso es la cuenta del dueño del proyecto
// (para el envio automatico), esto es si se habilita o no que un visitante
// conecte la suya propia. Apagado por defecto.
@ConfigurationProperties(prefix = "twilio-connect")
public record TwilioConnectProperties(
        @DefaultValue("false") boolean enabled
) {
}
