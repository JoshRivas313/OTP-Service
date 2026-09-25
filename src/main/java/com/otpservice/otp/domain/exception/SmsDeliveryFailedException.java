package com.otpservice.otp.domain.exception;

public class SmsDeliveryFailedException extends OtpDomainException {
    public SmsDeliveryFailedException() {
        super("SMS_DELIVERY_FAILED", "No se pudo enviar el SMS");
    }
}
