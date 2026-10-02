package com.otpservice.otp.adapter.exception;

import com.otpservice.otp.domain.exception.ErrorCode;
import com.otpservice.otp.domain.exception.OtpDomainException;

public class TwilioNotConnectedException extends OtpDomainException {
    public TwilioNotConnectedException() {
        super(ErrorCode.TWILIO_NOT_CONNECTED, "Primero conecta tu cuenta de Twilio");
    }
}
