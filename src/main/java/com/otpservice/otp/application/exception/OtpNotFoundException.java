package com.otpservice.otp.application.exception;

import com.otpservice.otp.domain.exception.ErrorCode;
import com.otpservice.otp.domain.exception.OtpDomainException;

public class OtpNotFoundException extends OtpDomainException {
    public OtpNotFoundException() {
        super(ErrorCode.OTP_NOT_FOUND, "No existe un código para este destino");
    }
}
