package com.otpservice.otp.adapter.out.sms;

import com.otpservice.otp.application.port.out.SmsSender;
import com.otpservice.otp.domain.valueobject.Cellphone;
import com.otpservice.otp.adapter.config.OtpProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sms.provider", havingValue = "console", matchIfMissing = true)
public class ConsoleSmsSender implements SmsSender {

    // Fuera de desarrollo (perfil prod) no se escribe el codigo: solo el destino enmascarado.
    private final OtpProperties properties;

    @Override
    public void send(Cellphone destination, String message) {
        if (properties.logCodes()) {
            log.info("[DEV][SMS] para={} mensaje=\"{}\"", destination.masked(), message);
        } else {
            log.info("[SMS] Código enviado a {}", destination.masked());
        }
    }
}
