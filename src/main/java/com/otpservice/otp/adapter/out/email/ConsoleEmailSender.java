package com.otpservice.otp.adapter.out.email;

import com.otpservice.otp.application.port.out.EmailSender;
import com.otpservice.otp.domain.valueobject.EmailAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "email.provider", havingValue = "console", matchIfMissing = true)
public class ConsoleEmailSender implements EmailSender {

    @Override
    public void send(EmailAddress destination, String message) {
        log.info("[DEV][EMAIL] para={} mensaje=\"{}\"", destination.masked(), message);
    }
}
