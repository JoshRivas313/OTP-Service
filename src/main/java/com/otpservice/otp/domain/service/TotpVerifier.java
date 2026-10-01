package com.otpservice.otp.domain.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;

public final class TotpVerifier {

    private TotpVerifier() {
    }

    public static CodeMatch verify(byte[] secret, String code, Instant now, int periodSeconds, int digits,
                                   int toleranceSteps, long lastUsedTimeStep) {
        return verify(secret, code, now, periodSeconds, digits, toleranceSteps, lastUsedTimeStep, 0);
    }

    public static CodeMatch verify(byte[] secret, String code, Instant now, int periodSeconds, int digits,
                                   int toleranceSteps, long lastUsedTimeStep, int expiredLookBackSteps) {
        long current = HmacOtpAlgorithm.timeStep(now, periodSeconds);
        boolean reused = false;
        for (long step = current - toleranceSteps; step <= current + toleranceSteps; step++) {
            if (!sameCode(HmacOtpAlgorithm.hotp(secret, step, digits), code)) {
                continue;
            }
            if (step > lastUsedTimeStep) {
                return CodeMatch.match(step);
            }
            reused = true;
        }
        if (reused) {
            return CodeMatch.reused();
        }
        long oldest = current - toleranceSteps - expiredLookBackSteps;
        for (long step = current - toleranceSteps - 1; step >= oldest; step--) {
            if (sameCode(HmacOtpAlgorithm.hotp(secret, step, digits), code)) {
                return step > lastUsedTimeStep ? CodeMatch.expired() : CodeMatch.reused();
            }
        }
        return CodeMatch.noMatch();
    }

    static boolean sameCode(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                actual.getBytes(StandardCharsets.US_ASCII));
    }
}
