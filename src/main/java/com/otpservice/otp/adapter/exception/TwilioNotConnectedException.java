package com.otpservice.otp.adapter.exception;

import com.otpservice.otp.domain.exception.OtpDomainException;

public class TwilioNotConnectedException extends OtpDomainException {
    public TwilioNotConnectedException() {
        super("TWILIO_NOT_CONNECTED", "Primero conectá tu cuenta de Twilio");
    }
}
