package com.otpservice.otp.adapter.exception;

import com.otpservice.otp.domain.exception.ErrorCode;
import com.otpservice.otp.domain.exception.OtpDomainException;

public class TwilioCredentialsInvalidException extends OtpDomainException {
    public TwilioCredentialsInvalidException() {
        this("Twilio rechazó esas credenciales o el Verify Service indicado");
    }

    public TwilioCredentialsInvalidException(String message) {
        super(ErrorCode.TWILIO_CREDENTIALS_INVALID, message);
    }
}
