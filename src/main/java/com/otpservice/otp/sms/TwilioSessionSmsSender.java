package com.otpservice.otp.sms;

import com.otpservice.otp.dto.valueobject.Cellphone;
import com.otpservice.otp.dto.valueobject.TwilioCredentials;
import com.otpservice.otp.exception.ErrorCode;
import com.otpservice.otp.exception.OtpException;
import com.twilio.exception.ApiException;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// Analogo a TwilioSmsSender, pero para el modo "trae tu propia cuenta": en vez
// de credenciales globales del servidor, arma el cliente y el numero remitente
// a partir de la TwilioCredentials de la sesion del visitante. Se usa cuando
// el OTP lo genera nuestro backend (Mongo+HMAC) y solo necesitamos que el SMS
// salga por la cuenta conectada, sin pasar por Twilio Verify.
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
        } catch (ApiException exception) {
            throw new OtpException(ErrorCode.SMS_DELIVERY_FAILED);
        }
    }

    private TwilioRestClient buildClient(TwilioCredentials credentials) {
        return new TwilioRestClient.Builder(credentials.getAccountSid(), credentials.getAuthToken()).build();
    }
}
