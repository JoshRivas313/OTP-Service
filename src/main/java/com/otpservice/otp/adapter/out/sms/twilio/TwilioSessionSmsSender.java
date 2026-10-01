package com.otpservice.otp.adapter.out.sms.twilio;

import com.otpservice.otp.domain.valueobject.Cellphone;
import com.otpservice.otp.domain.valueobject.TwilioCredentials;
import org.springframework.stereotype.Component;

@Component
public class TwilioSessionSmsSender {

    public void send(TwilioCredentials credentials, Cellphone destination, String message) {
        TwilioGateway.sendSms(
                TwilioGateway.client(credentials.getAccountSid(), credentials.getAuthToken()),
                credentials.getPhoneNumber(), destination, message, "sesion");
    }
}
