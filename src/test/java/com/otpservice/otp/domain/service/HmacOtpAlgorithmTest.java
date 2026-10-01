package com.otpservice.otp.domain.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacOtpAlgorithmTest {

    // Secreto de los apendices de los RFC 4226 y 6238
    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @ParameterizedTest(name = "contador {0} -> {1}")
    @CsvSource({
            "0, 755224", "1, 287082", "2, 359152", "3, 969429", "4, 338314",
            "5, 254676", "6, 287922", "7, 162583", "8, 399871", "9, 520489"
    })
    void hotpCoincideConElApendiceDDelRfc4226(long counter, String expected) {
        assertThat(HmacOtpAlgorithm.hotp(RFC_SECRET, counter, 6)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "t={0} -> {1}")
    @CsvSource({
            "59, 94287082",
            "1111111109, 07081804",
            "1111111111, 14050471",
            "1234567890, 89005924",
            "2000000000, 69279037",
            "20000000000, 65353130"
    })
    void totpCoincideConElApendiceBDelRfc6238(long epochSecond, String expected) {
        assertThat(HmacOtpAlgorithm.totp(RFC_SECRET, Instant.ofEpochSecond(epochSecond), 30, 8)).isEqualTo(expected);
    }

    @Test
    void conservaLosCerosIniciales() {
        assertThat(HmacOtpAlgorithm.totp(RFC_SECRET, Instant.ofEpochSecond(1111111109), 30, 8)).startsWith("0");
    }

    @Test
    void rechazaDigitosFueraDeSeisAOcho() {
        assertThatThrownBy(() -> HmacOtpAlgorithm.hotp(RFC_SECRET, 0, 5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HmacOtpAlgorithm.hotp(RFC_SECRET, 0, 9)).isInstanceOf(IllegalArgumentException.class);
    }
}
