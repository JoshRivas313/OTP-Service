package com.otpservice.otp.domain.valueobject;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.otpservice.otp.domain.exception.InvalidOtpException;

import java.security.SecureRandom;
import java.util.regex.Pattern;

public final class OtpCode {

    public static final int MIN_LENGTH = 4;
    public static final int MAX_LENGTH = 10;
    private static final Pattern PATTERN = Pattern.compile("^\\d{" + MIN_LENGTH + "," + MAX_LENGTH + "}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String value;

    @JsonCreator
    public OtpCode(String value) {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "El código debe tener entre " + MIN_LENGTH + " y " + MAX_LENGTH + " dígitos");
        }
        this.value = value;
    }

    // Para el codigo que escribe el usuario: un formato invalido es un codigo incorrecto, no un error de la peticion.
    public static OtpCode parse(String value) {
        try {
            return new OtpCode(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidOtpException();
        }
    }

    public static OtpCode generate(int digits) {
        StringBuilder builder = new StringBuilder(digits);
        for (int i = 0; i < digits; i++) {
            builder.append(RANDOM.nextInt(10));
        }
        return new OtpCode(builder.toString());
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "OtpCode[oculto]";
    }
}
