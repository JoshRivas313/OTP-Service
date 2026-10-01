package com.otpservice.otp.support;

public final class TestBase32 {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private TestBase32() {
    }

    public static byte[] decode(String value) {
        byte[] out = new byte[value.length() * 5 / 8];
        int buffer = 0;
        int bits = 0;
        int index = 0;
        for (char c : value.toCharArray()) {
            buffer = (buffer << 5) | ALPHABET.indexOf(c);
            bits += 5;
            if (bits >= 8) {
                out[index++] = (byte) (buffer >> (bits - 8));
                bits -= 8;
            }
        }
        return out;
    }
}
