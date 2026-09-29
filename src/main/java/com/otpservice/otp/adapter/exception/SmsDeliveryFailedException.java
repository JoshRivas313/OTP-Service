package com.otpservice.otp.adapter.exception;

import com.otpservice.otp.domain.exception.OtpDomainException;

public class SmsDeliveryFailedException extends OtpDomainException {
    public SmsDeliveryFailedException() {
        super("SMS_DELIVERY_FAILED", "No se pudo enviar el SMS");
    }
}
