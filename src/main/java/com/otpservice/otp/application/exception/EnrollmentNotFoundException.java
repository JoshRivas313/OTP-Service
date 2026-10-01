package com.otpservice.otp.application.exception;

import com.otpservice.otp.domain.exception.OtpDomainException;

public class EnrollmentNotFoundException extends OtpDomainException {
    public EnrollmentNotFoundException() {
        super("ENROLLMENT_NOT_FOUND", "No hay una app autenticadora registrada para este correo y tipo");
    }
}
