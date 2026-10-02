package com.otpservice.otp.adapter.out.email;

import com.otpservice.otp.application.port.out.EmailSender;
import com.otpservice.otp.domain.valueobject.EmailAddress;
import com.otpservice.otp.adapter.config.OtpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "email.provider", havingValue = "console", matchIfMissing = true)
public class ConsoleEmailSender implements EmailSender {

    // Fuera de desarrollo (perfil prod) no se escribe el codigo: solo el destino enmascarado.
    private final OtpProperties properties;

    @Override
    public void send(EmailAddress destination, String message) {
        if (properties.logCodes()) {
            log.info("[DEV][EMAIL] para={} mensaje=\"{}\"", destination.masked(), message);
        } else {
            log.info("[EMAIL] Código enviado a {}", destination.masked());
        }
    }
}
