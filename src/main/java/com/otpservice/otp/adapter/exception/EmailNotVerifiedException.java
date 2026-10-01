package com.otpservice.otp.adapter.exception;

import com.otpservice.otp.domain.exception.OtpDomainException;

public class EmailNotVerifiedException extends OtpDomainException {
    public EmailNotVerifiedException() {
        super("EMAIL_NOT_VERIFIED", "Primero verificá este correo con el código que te enviamos");
    }
}
