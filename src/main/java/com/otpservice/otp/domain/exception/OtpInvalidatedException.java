package com.otpservice.otp.domain.exception;

public class OtpInvalidatedException extends OtpDomainException {
    public OtpInvalidatedException() {
        super(ErrorCode.OTP_INVALIDATED, "El código fue reemplazado por uno más reciente");
    }
}
