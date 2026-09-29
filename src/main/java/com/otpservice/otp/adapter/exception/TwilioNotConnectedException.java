package com.otpservice.otp.adapter.exception;

import com.otpservice.otp.domain.exception.OtpDomainException;

/**
 * Thrown only from TwilioOtpHttpAdapter when the HttpSession has no
 * connected TwilioCredentials — a purely HTTP-session concern.
 */
public class TwilioNotConnectedException extends OtpDomainException {
    public TwilioNotConnectedException() {
        super("TWILIO_NOT_CONNECTED", "Primero conectá tu cuenta de Twilio");
    }
}
