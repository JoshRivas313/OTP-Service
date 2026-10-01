package com.otpservice.otp.domain.model;

import com.otpservice.otp.domain.exception.OtpAlreadyUsedException;
import com.otpservice.otp.domain.exception.OtpBlockedException;
import com.otpservice.otp.domain.exception.OtpExpiredException;
import com.otpservice.otp.domain.exception.OtpInvalidatedException;
import com.otpservice.otp.domain.valueobject.Destination;
import com.otpservice.otp.domain.valueobject.ValidityWindow;
import com.otpservice.otp.domain.valueobject.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@AllArgsConstructor
public class Otp {

    private final String id;
    private final String destination;
    private final String codeHash;
    private final int digits;
    private final ValidityWindow validityWindow;
    private final VerificationStatus verificationStatus;
    private final Instant purgeAt;

    public record IssueRequest(Destination destination, String codeHash, int digits,
                                ValidityWindow validityWindow, Instant purgeAt) {
    }

    public static Otp issue(IssueRequest request) {
        return Otp.builder()
                .destination(request.destination().getValue())
                .codeHash(request.codeHash())
                .digits(request.digits())
                .validityWindow(request.validityWindow())
                .verificationStatus(new VerificationStatus())
                .purgeAt(request.purgeAt())
                .build();
    }

    // Orden de las comprobaciones: es el que determina que error ve el cliente.
    public void ensureVerifiable(Instant now, int maxAllowedAttempts) {
        if (isInvalidated()) {
            throw new OtpInvalidatedException();
        }
        if (isUsed()) {
            throw new OtpAlreadyUsedException();
        }
        if (isExpired(now)) {
            throw new OtpExpiredException();
        }
        if (isBlocked(maxAllowedAttempts)) {
            throw new OtpBlockedException();
        }
    }

    public boolean isExpired(Instant now) {
        return validityWindow.isExpired(now);
    }

    public boolean isUsed() {
        return verificationStatus.isUsed();
    }

    public boolean isInvalidated() {
        return verificationStatus.isInvalidated();
    }

    public boolean isBlocked(int maxAllowedAttempts) {
        return verificationStatus.isBlocked(maxAllowedAttempts);
    }

    public int getAttempts() {
        return verificationStatus.getAttempts();
    }

    @Override
    public String toString() {
        return "Otp[id=%s, destination=%s, digits=%d]".formatted(id, destination, digits);
    }
}
