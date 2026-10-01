package com.otpservice.otp.adapter.exception;

import com.otpservice.otp.domain.exception.OtpDomainException;

public class EmailDeliveryFailedException extends OtpDomainException {
    public EmailDeliveryFailedException() {
        super("EMAIL_DELIVERY_FAILED", "No se pudo enviar el correo");
    }
}
