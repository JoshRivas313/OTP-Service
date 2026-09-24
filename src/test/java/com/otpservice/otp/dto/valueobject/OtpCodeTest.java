package com.otpservice.otp.dto.valueobject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OtpCodeTest {

    @Test
    void generaSoloDigitos() {
        assertThat(OtpCode.generate(6).getValue()).containsOnlyDigits();
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 6, 8, 10})
    void respetaLaCantidadDeDigitos(int digits) {
        assertThat(OtpCode.generate(digits).getValue()).hasSize(digits);
    }

    @Test
    void conservaCerosIniciales() {
        assertThat(new OtpCode("000123").getValue()).isEqualTo("000123");
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345a", "abcdef", "123", "12345678901", ""})
    void rechazaValoresInvalidos(String invalido) {
        assertThatThrownBy(() -> new OtpCode(invalido)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noRevelaElCodigoEnToString() {
        assertThat(new OtpCode("472981")).hasToString("OtpCode[oculto]");
    }
}
