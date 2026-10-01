package com.otpservice.otp.adapter.out.sms.twilio;

import com.otpservice.otp.adapter.exception.TwilioCredentialsInvalidException;
import com.otpservice.otp.domain.valueobject.TwilioCredentials;
import com.twilio.exception.ApiException;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.api.v2010.Account;
import com.twilio.rest.api.v2010.account.OutgoingCallerId;
import com.twilio.rest.verify.v2.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
public class TwilioVerifyService {

    public void validateCredentials(TwilioCredentials credentials) {
        try {
            Service.fetcher(credentials.getVerifyServiceSid()).fetch(TwilioGateway.client(credentials.getAccountSid(), credentials.getAuthToken()));
        } catch (ApiException exception) {
            throw new TwilioCredentialsInvalidException();
        }
    }

    // Una cuenta de prueba solo entrega a sus numeros verificados; una de pago con numeros verificados tambien se limita a ellos.
    public TwilioAccountInfo fetchAccountInfo(TwilioCredentials credentials) {
        try {
            TwilioRestClient client = TwilioGateway.client(credentials.getAccountSid(), credentials.getAuthToken());
            boolean trial = Account.fetcher(credentials.getAccountSid()).fetch(client).getType() == Account.Type.TRIAL;
            Set<String> verified = new HashSet<>();
            for (OutgoingCallerId callerId : OutgoingCallerId.reader().read(client)) {
                verified.add(callerId.getPhoneNumber().getEndpoint());
            }
            return new TwilioAccountInfo(trial || !verified.isEmpty(), Set.copyOf(verified));
        } catch (ApiException exception) {
            log.warn("No se pudo consultar los numeros verificados de la cuenta de Twilio: {}", exception.getMessage());
            return TwilioAccountInfo.unrestricted();
        }
    }

}
