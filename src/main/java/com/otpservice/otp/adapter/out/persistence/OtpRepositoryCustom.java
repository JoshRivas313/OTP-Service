package com.otpservice.otp.adapter.out.persistence;

import com.otpservice.otp.adapter.out.persistence.document.OtpDocument;

import java.time.Instant;
import java.util.Optional;

public interface OtpRepositoryCustom {

    long invalidateActive(String destination);

    Optional<OtpDocument> claimIfMatches(String id, String codeHash, Instant now, int maxAttempts);

    Optional<OtpDocument> registerFailedAttempt(String id);
}
