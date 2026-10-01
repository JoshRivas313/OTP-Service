package com.otpservice.otp.adapter.exception;

import com.otpservice.otp.domain.exception.ErrorCode;
import com.otpservice.otp.domain.exception.OtpDomainException;

public class DestinationNotVerifiedException extends OtpDomainException {
    public DestinationNotVerifiedException() {
        super(ErrorCode.DESTINATION_NOT_VERIFIED,
                "Tu cuenta de Twilio solo puede enviar a los números que verificaste en su consola");
    }
}
