package com.otpservice.otp.domain.exception;

public class EnrollmentNotConfirmedException extends OtpDomainException {
    public EnrollmentNotConfirmedException() {
        super("ENROLLMENT_NOT_CONFIRMED", "Primero confirmá el registro con el código que muestra tu app");
    }
}
