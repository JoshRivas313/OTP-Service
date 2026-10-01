package com.otpservice.otp.adapter.exception;

import com.otpservice.otp.domain.exception.ErrorCode;
import com.otpservice.otp.domain.exception.OtpDomainException;

public class SmsDeliveryFailedException extends OtpDomainException {
    public SmsDeliveryFailedException() {
        this("No se pudo enviar el SMS");
    }

    public SmsDeliveryFailedException(String message) {
        super(ErrorCode.SMS_DELIVERY_FAILED, message);
    }
}
