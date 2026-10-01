package com.otpservice.otp.application.protocol;

public record VerifiedCode(Long counter, Long timeStep) {

    public static VerifiedCode random() {
        return new VerifiedCode(null, null);
    }
}
