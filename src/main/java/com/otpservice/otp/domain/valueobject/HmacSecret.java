package com.otpservice.otp.domain.valueobject;

import java.security.SecureRandom;
import java.util.Arrays;

// 20 bytes: el tamano que recomienda el RFC 4226.
public final class HmacSecret {

    public static final int LENGTH_BYTES = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] value;

    public HmacSecret(byte[] value) {
        if (value == null || value.length != LENGTH_BYTES) {
            throw new IllegalArgumentException("El secreto debe tener " + LENGTH_BYTES + " bytes");
        }
        this.value = value.clone();
    }

    public static HmacSecret generate() {
        byte[] bytes = new byte[LENGTH_BYTES];
        RANDOM.nextBytes(bytes);
        return new HmacSecret(bytes);
    }

    public byte[] bytes() {
        return value.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof HmacSecret s && Arrays.equals(value, s.value);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(value);
    }

    @Override
    public String toString() {
        return "HmacSecret[oculto]";
    }
}
