package com.otpservice.otp.domain.service;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Base32Test {

    // RFC 4648, seccion 10, sin el relleno "="
    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            "'', ''",
            "f, MY",
            "fo, MZXQ",
            "foo, MZXW6",
            "foob, MZXW6YQ",
            "fooba, MZXW6YTB",
            "foobar, MZXW6YTBOI"
    })
    void codificaLosVectoresDelRfc4648(String input, String expected) {
        assertThat(Base32.encode(input.getBytes(StandardCharsets.US_ASCII))).isEqualTo(expected);
    }
}
