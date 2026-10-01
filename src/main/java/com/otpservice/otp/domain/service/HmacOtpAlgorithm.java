package com.otpservice.otp.domain.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.time.Instant;

// RFC 4226 y RFC 6238 con HMAC-SHA1, el que usan las apps autenticadoras.
public final class HmacOtpAlgorithm {

    private static final String HMAC_SHA1 = "HmacSHA1";
    private static final int[] POWERS_OF_TEN = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000};

    private HmacOtpAlgorithm() {
    }

    public static String hotp(byte[] secret, long counter, int digits) {
        if (digits < 6 || digits > 8) {
            throw new IllegalArgumentException("HOTP y TOTP usan entre 6 y 8 dígitos");
        }
        byte[] hash = hmacSha1(secret, ByteBuffer.allocate(Long.BYTES).putLong(counter).array());

        int offset = hash[hash.length - 1] & 0x0f;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);

        String code = Integer.toString(binary % POWERS_OF_TEN[digits]);
        return "0".repeat(digits - code.length()) + code;
    }

    public static long timeStep(Instant instant, int stepSeconds) {
        return Math.floorDiv(instant.getEpochSecond(), stepSeconds);
    }

    public static String totp(byte[] secret, Instant instant, int stepSeconds, int digits) {
        return hotp(secret, timeStep(instant, stepSeconds), digits);
    }

    private static byte[] hmacSha1(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA1);
            mac.init(new SecretKeySpec(key, HMAC_SHA1));
            return mac.doFinal(message);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo calcular el HMAC del código", exception);
        }
    }
}
