package com.otpservice.otp.domain.exception;

public class AuthenticatorLockedException extends OtpDomainException {
    public AuthenticatorLockedException(long minutes) {
        super("AUTHENTICATOR_LOCKED",
                "Demasiados códigos incorrectos. Probá de nuevo en %d min".formatted(minutes));
    }
}
