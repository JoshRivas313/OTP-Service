package com.otpservice.otp.sms;

import com.otpservice.otp.dto.valueobject.TwilioCredentials;
import com.otpservice.otp.exception.ErrorCode;
import com.otpservice.otp.exception.OtpException;
import com.twilio.exception.ApiException;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.verify.v2.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// Solo valida que el Verify Service SID exista y sea accesible con esas
// credenciales, para el paso de "Conectar con Twilio". La generacion y
// verificacion del OTP ya no pasan por Twilio Verify (ver TwilioOtpServiceImpl):
// el backend propio genera el codigo y TwilioSessionSmsSender lo manda como
// SMS simple, asi digits y durationSeconds funcionan de verdad.
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
