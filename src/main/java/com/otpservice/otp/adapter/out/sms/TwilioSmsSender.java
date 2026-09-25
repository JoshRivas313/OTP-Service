package com.otpservice.otp.adapter.out.sms;

import com.otpservice.otp.adapter.config.SmsProperties;
import com.otpservice.otp.domain.port.output.SmsSender;
import com.otpservice.otp.domain.valueobject.Cellphone;
import com.otpservice.otp.domain.exception.SmsDeliveryFailedException;
import com.twilio.Twilio;
import com.twilio.exception.TwilioException;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sms.provider", havingValue = "twilio")
public class TwilioSmsSender implements SmsSender {

    private final SmsProperties properties;

    @PostConstruct
    void initialize() {
        SmsProperties.Twilio twilio = properties.twilio();
        if (twilio.accountSid().isBlank() || twilio.authToken().isBlank() || twilio.phoneNumber().isBlank()) {
            throw new IllegalStateException("sms.provider=twilio requiere TWILIO_ACCOUNT_SID, "
                    + "TWILIO_AUTH_TOKEN y TWILIO_PHONE_NUMBER");
        }
        Twilio.init(twilio.accountSid(), twilio.authToken());
        log.info("Proveedor de SMS = twilio");
    }

    @Override
    public void send(Cellphone destination, String message) {
        try {
            Message sent = Message.creator(
                    new PhoneNumber(destination.getValue()),
                    new PhoneNumber(properties.twilio().phoneNumber()),
                    message).create();

            log.info("SMS entregado a Twilio para={} sid={}", destination.masked(), sent.getSid());
        } catch (TwilioException exception) {
            throw new SmsDeliveryFailedException();
        }
    }
}
