package com.otpservice.otp.application.exception;

import com.otpservice.otp.domain.exception.OtpDomainException;

/**
 * Thrown by application/usecase when a persistence lookup finds nothing —
 * not a rule the Otp model itself evaluates, but an orchestration decision
 * over an empty OtpPersistencePort result.
 */
public class OtpNotFoundException extends OtpDomainException {
    public OtpNotFoundException() {
        super("OTP_NOT_FOUND", "No existe un código para este número");
    }
}
