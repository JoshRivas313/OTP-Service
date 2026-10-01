package com.otpservice.otp.domain.service;

// lookAhead negativo: no hay codigos pendientes.
public final class HotpVerifier {

    private HotpVerifier() {
    }

    public static CodeMatch verify(byte[] secret, String code, long expectedCounter, long lookAhead, long lookBack,
                                   int digits) {
        for (long counter = expectedCounter; counter <= expectedCounter + lookAhead; counter++) {
            if (TotpVerifier.sameCode(HmacOtpAlgorithm.hotp(secret, counter, digits), code)) {
                return CodeMatch.match(counter);
            }
        }
        for (long counter = Math.max(0, expectedCounter - lookBack); counter < expectedCounter; counter++) {
            if (TotpVerifier.sameCode(HmacOtpAlgorithm.hotp(secret, counter, digits), code)) {
                return CodeMatch.reused();
            }
        }
        return CodeMatch.noMatch();
    }
}
