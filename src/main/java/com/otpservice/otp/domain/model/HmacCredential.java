package com.otpservice.otp.domain.model;

import com.otpservice.otp.domain.valueobject.EncryptedSecret;
import com.otpservice.otp.domain.valueobject.HmacType;
import com.otpservice.otp.domain.valueobject.Purpose;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

// counter: proximo contador que se acepta. issuedCounter: proximo que se emite.
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor
public class HmacCredential {

    public static final long NO_TIME_STEP_USED = -1;

    private final String id;
    private final String destination;
    private final HmacType type;
    private final Purpose purpose;
    private final EncryptedSecret secret;
    private final int digits;
    private final int periodSeconds;
    private final long counter;
    private final long issuedCounter;
    private final long lastUsedTimeStep;
    private final int failedAttempts;
    private final Instant lockedUntil;
    private final Instant createdAt;

    public static HmacCredential create(String destination, HmacType type, Purpose purpose, EncryptedSecret secret,
                                        int digits, int periodSeconds, Instant now) {
        return HmacCredential.builder()
                .destination(destination)
                .type(type)
                .purpose(purpose)
                .secret(secret)
                .digits(digits)
                .periodSeconds(type.effectivePeriod(periodSeconds))
                .counter(0)
                .issuedCounter(0)
                .lastUsedTimeStep(NO_TIME_STEP_USED)
                .failedAttempts(0)
                .createdAt(now)
                .build();
    }

    // Datos asociados del cifrado del secreto: un secreto copiado a otro destino, tipo o proposito no se descifra.
    public static String secretContext(String destination, HmacType type, Purpose purpose) {
        return destination + "|" + type.name() + "|" + purpose.name();
    }

    public String secretContext() {
        return secretContext(destination, type, purpose);
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    @Override
    public String toString() {
        return "HmacCredential[id=%s, type=%s]".formatted(id, type);
    }
}
