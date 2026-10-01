package com.otpservice.otp.adapter.out.sms;

import com.otpservice.otp.adapter.config.SmsProperties;
import com.otpservice.otp.adapter.out.sms.twilio.TwilioGateway;
import com.otpservice.otp.application.port.out.SmsSender;
import com.otpservice.otp.domain.valueobject.Cellphone;
import com.twilio.http.TwilioRestClient;
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
    private TwilioRestClient client;

    @PostConstruct
    void initialize() {
        SmsProperties.Twilio twilio = properties.twilio();
        if (twilio.accountSid().isBlank() || twilio.authToken().isBlank() || twilio.phoneNumber().isBlank()) {
            throw new IllegalStateException("sms.provider=twilio requiere TWILIO_ACCOUNT_SID, "
                    + "TWILIO_AUTH_TOKEN y TWILIO_PHONE_NUMBER");
        }
        this.client = TwilioGateway.client(twilio.accountSid(), twilio.authToken());
        log.info("Proveedor de SMS = twilio");
    }

    @Override
    public void send(Cellphone destination, String message) {
        TwilioGateway.sendSms(client, properties.twilio().phoneNumber(), destination, message, "servidor");
    }
}
