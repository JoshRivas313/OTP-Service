package com.otpservice.otp.adapter.out.sms.twilio;

import com.otpservice.otp.application.port.out.SmsSender;
import com.otpservice.otp.domain.valueobject.Cellphone;
import com.otpservice.otp.domain.valueobject.TwilioCredentials;
import com.otpservice.otp.adapter.exception.SmsDeliveryFailedException;
import com.twilio.exception.TwilioException;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TwilioSessionSmsSender {

    public void send(TwilioCredentials credentials, Cellphone destination, String message) {
        try {
            Message sent = Message.creator(
                    new PhoneNumber(destination.getValue()),
                    new PhoneNumber(credentials.getPhoneNumber()),
                    message).create(buildClient(credentials));
            log.info("SMS entregado a Twilio (sesion) para={} sid={}", destination.masked(), sent.getSid());
        } catch (TwilioException exception) {
            Integer code = TwilioErrorMessages.codeOf(exception);
            log.warn("Twilio rechazo el envio (sesion) para={} codigo={}: {}", destination.masked(), code, exception.getMessage());
            throw new SmsDeliveryFailedException(TwilioErrorMessages.describe(code));
        }
    }

    private TwilioRestClient buildClient(TwilioCredentials credentials) {
        return new TwilioRestClient.Builder(credentials.getAccountSid(), credentials.getAuthToken()).build();
    }
}
