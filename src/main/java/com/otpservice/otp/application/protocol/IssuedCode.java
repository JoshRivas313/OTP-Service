package com.otpservice.otp.application.protocol;

// expiresInSeconds es null en HOTP; counter solo existe en HOTP y timeStep solo en TOTP.
public record IssuedCode(String code, Long expiresInSeconds, Long counter, Long timeStep) {
}
