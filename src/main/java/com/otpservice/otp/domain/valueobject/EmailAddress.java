package com.otpservice.otp.domain.valueobject;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public final class EmailAddress implements Destination {

    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*\\.[A-Za-z]{2,}$");
    private static final int MAX_LENGTH = 254;

    private final String value;

    @JsonCreator
    public EmailAddress(String value) {
        String candidate = value == null ? null : value.trim();
        ensureIsValid(candidate);
        this.value = candidate.toLowerCase(Locale.ROOT);
    }

    @Override
    @JsonValue
    public String getValue() {
        return value;
    }

    @Override
    public String masked() {
        int at = value.indexOf('@');
        return value.charAt(0) + "***" + value.substring(at);
    }

    private static void ensureIsValid(String candidate) {
        Optional.ofNullable(candidate)
                .filter(v -> v.length() <= MAX_LENGTH && PATTERN.matcher(v).matches())
                .orElseThrow(() -> new IllegalArgumentException("Correo electrónico no válido"));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        return other instanceof EmailAddress e && value.equals(e.value);
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
