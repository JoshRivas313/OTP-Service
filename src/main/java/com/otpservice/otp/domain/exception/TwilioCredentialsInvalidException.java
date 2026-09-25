package com.otpservice.otp.domain.exception;

public class TwilioCredentialsInvalidException extends OtpDomainException {
    public TwilioCredentialsInvalidException() {
        this("Twilio rechazó esas credenciales o el Verify Service indicado");
    }

    public TwilioCredentialsInvalidException(String message) {
        super("TWILIO_CREDENTIALS_INVALID", message);
    }
}
