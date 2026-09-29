package com.otpservice.otp.domain.model;

import com.otpservice.otp.domain.valueobject.Cellphone;
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
    private final String cellphone;
    private final String codeHash;
    private final int digits;
    private final ValidityWindow validityWindow;
    private final VerificationStatus verificationStatus;
    private final Instant purgeAt;

    public record IssueRequest(Cellphone cellphone, String codeHash, int digits,
                                ValidityWindow validityWindow, Instant purgeAt) {
    }

    public static Otp issue(IssueRequest request) {
        return Otp.builder()
                .cellphone(request.cellphone().getValue())
                .codeHash(request.codeHash())
                .digits(request.digits())
                .validityWindow(request.validityWindow())
                .verificationStatus(new VerificationStatus())
                .purgeAt(request.purgeAt())
                .build();
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
        return "Otp[id=%s, cellphone=%s, digits=%d]".formatted(id, cellphone, digits);
    }
}
