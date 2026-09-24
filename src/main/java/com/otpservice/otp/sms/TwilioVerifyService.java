package com.otpservice.otp.sms;

import com.otpservice.otp.dto.valueobject.Cellphone;
import com.otpservice.otp.dto.valueobject.TwilioCredentials;
import com.otpservice.otp.exception.ErrorCode;
import com.otpservice.otp.exception.OtpException;
import com.twilio.exception.ApiException;
import com.twilio.http.TwilioRestClient;
import com.twilio.rest.verify.v2.Service;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

// Twilio Verify para el modo "trae tu propia cuenta": a diferencia de
// TwilioSmsSender, nunca llama a Twilio.init() de forma global. El cliente
// se construye por request a partir de la credencial de la sesion, para que
// dos visitantes conectados a la vez no se pisen entre si.
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

    public void sendVerificationCode(TwilioCredentials credentials, Cellphone destination) {
        try {
            Verification.creator(credentials.getVerifyServiceSid(), destination.getValue(), "sms")
                    .create(buildClient(credentials));
            log.info("Verificacion Twilio enviada para={}", destination.masked());
        } catch (ApiException exception) {
            throw new OtpException(ErrorCode.SMS_DELIVERY_FAILED);
        }
    }

    public boolean checkVerificationCode(TwilioCredentials credentials, Cellphone destination, String code) {
        try {
            VerificationCheck check = VerificationCheck.creator(credentials.getVerifyServiceSid())
                    .setTo(destination.getValue())
                    .setCode(code)
                    .create(buildClient(credentials));
            return "approved".equals(check.getStatus());
        } catch (ApiException exception) {
            return false;
        }
    }

    private TwilioRestClient buildClient(TwilioCredentials credentials) {
        return new TwilioRestClient.Builder(credentials.getAccountSid(), credentials.getAuthToken()).build();
    }
}
