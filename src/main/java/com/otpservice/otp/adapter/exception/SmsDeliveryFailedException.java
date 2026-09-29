package com.otpservice.otp.adapter.exception;

import com.otpservice.otp.domain.exception.OtpDomainException;

/**
 * Thrown only by adapter/out/sms/* implementations (Twilio, Infobip API
 * failures) — never by application/usecase. Lives here, not in
 * application/exception, because the use cases never catch or construct it.
 */
public class SmsDeliveryFailedException extends OtpDomainException {
    public SmsDeliveryFailedException() {
        super("SMS_DELIVERY_FAILED", "No se pudo enviar el SMS");
    }
}
