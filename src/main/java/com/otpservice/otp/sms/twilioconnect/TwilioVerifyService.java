package com.otpservice.otp.sms.twilioconnect;

import com.otpservice.otp.dto.valueobject.TwilioCredentials;
import com.otpservice.otp.exception.ErrorCode;
import com.otpservice.otp.exception.OtpException;
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
            throw new OtpException(ErrorCode.TWILIO_CREDENTIALS_INVALID);
        }
    }

    private TwilioRestClient buildClient(TwilioCredentials credentials) {
        return new TwilioRestClient.Builder(credentials.getAccountSid(), credentials.getAuthToken()).build();
    }
}
