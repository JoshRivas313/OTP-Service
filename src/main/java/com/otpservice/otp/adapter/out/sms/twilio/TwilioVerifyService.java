package com.otpservice.otp.adapter.out.sms.twilio;

import com.otpservice.otp.adapter.exception.TwilioCredentialsInvalidException;
import com.otpservice.otp.domain.valueobject.TwilioCredentials;
import com.twilio.exception.ApiException;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.verify.v2.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TwilioVerifyService {

    public void validateCredentials(TwilioCredentials credentials) {
        try {
            Service.fetcher(credentials.getVerifyServiceSid()).fetch(buildClient(credentials));
        } catch (ApiException exception) {
            throw new TwilioCredentialsInvalidException();
        }
    }

    private TwilioRestClient buildClient(TwilioCredentials credentials) {
        return new TwilioRestClient.Builder(credentials.getAccountSid(), credentials.getAuthToken()).build();
    }
}
