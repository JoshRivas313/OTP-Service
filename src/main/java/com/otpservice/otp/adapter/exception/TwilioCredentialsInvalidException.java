package com.otpservice.otp.adapter.exception;

import com.otpservice.otp.domain.exception.OtpDomainException;

/**
 * Thrown only from adapter/ code: TwilioVerifyService (SDK validation) and
 * TwilioConnectHttpAdapter (request parsing).
 */
public class TwilioCredentialsInvalidException extends OtpDomainException {
    public TwilioCredentialsInvalidException() {
        this("Twilio rechazó esas credenciales o el Verify Service indicado");
    }

    public TwilioCredentialsInvalidException(String message) {
        super("TWILIO_CREDENTIALS_INVALID", message);
    }
}
