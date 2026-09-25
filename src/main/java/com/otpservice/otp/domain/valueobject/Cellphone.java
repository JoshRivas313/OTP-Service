package com.otpservice.otp.domain.valueobject;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public final class Cellphone {

    private static final Pattern PATTERN = Pattern.compile("^(\\+51)?9\\d{8}$");
    private static final String COUNTRY_CODE = "+51";

    private final String value;

    @JsonCreator
    public Cellphone(String value) {
        String candidate = value == null ? null : value.trim();
        ensureIsValid(candidate);
        this.value = normalize(candidate);
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public String masked() {
        int hidden = value.length() - 3;
        return "*".repeat(hidden) + value.substring(hidden);
    }

    private static String normalize(String candidate) {
        return candidate.startsWith(COUNTRY_CODE) ? candidate : COUNTRY_CODE + candidate;
    }

    private void ensureIsValid(String candidate) {
        Optional.ofNullable(candidate)
                .filter(v -> PATTERN.matcher(v).matches())
                .orElseThrow(() -> new IllegalArgumentException("Número de teléfono no válido"));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        return other instanceof Cellphone c && value.equals(c.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return masked();
    }
}
