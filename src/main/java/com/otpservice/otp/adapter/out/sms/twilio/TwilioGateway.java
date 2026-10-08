package com.otpservice.otp.adapter.out.sms.twilio;

import com.otpservice.otp.adapter.exception.SmsDeliveryFailedException;
import com.otpservice.otp.domain.valueobject.Cellphone;
import com.twilio.exception.TwilioException;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import lombok.extern.slf4j.Slf4j;

// Punto unico de acceso a Twilio: el cliente y el envio de SMS, con la traduccion de sus errores.
@Slf4j
public final class TwilioGateway {

    private TwilioGateway() {
    }

    public static TwilioRestClient client(String accountSid, String authToken) {
        return new TwilioRestClient.Builder(accountSid, authToken).build();
    }

    // origin: de donde salen las credenciales ("servidor" o "sesion"), solo para el log.
    public static void sendSms(TwilioRestClient client, String from, Cellphone destination, String message,
                               String origin) {
        try {
            Message sent = Message.creator(
                    new PhoneNumber(destination.getValue()), new PhoneNumber(from), message).create(client);
            log.info("SMS entregado a Twilio ({}) para={} sid={}", origin, destination.masked(), sent.getSid());
        } catch (TwilioException exception) {
            Integer code = TwilioErrorMessages.codeOf(exception);
            // El mensaje de Twilio suele repetir el numero completo: se enmascara antes de escribirlo.
            log.warn("Twilio rechazo el envio ({}) para={} codigo={}: {}", origin, destination.masked(), code,
                    String.valueOf(exception.getMessage()).replace(destination.getValue(), destination.masked()));
            throw new SmsDeliveryFailedException(TwilioErrorMessages.describe(code));
        }
    }
}
