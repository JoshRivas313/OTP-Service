package com.otpservice.otp.domain.exception;

public class OtpInvalidatedException extends OtpDomainException {
    public OtpInvalidatedException() {
        super("OTP_INVALIDATED", "El código fue reemplazado por uno más reciente");
    }
}
