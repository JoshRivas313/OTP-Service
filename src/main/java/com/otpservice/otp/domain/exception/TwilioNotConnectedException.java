package com.otpservice.otp.domain.exception;

public class TwilioNotConnectedException extends OtpDomainException {
    public TwilioNotConnectedException() {
        super("TWILIO_NOT_CONNECTED", "Primero conectá tu cuenta de Twilio");
    }
}
