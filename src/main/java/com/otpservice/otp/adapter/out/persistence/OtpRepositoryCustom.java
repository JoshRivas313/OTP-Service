package com.otpservice.otp.adapter.out.persistence;

import com.otpservice.otp.domain.model.OtpDocument;

import java.time.Instant;
import java.util.Optional;

public interface OtpRepositoryCustom {

    long invalidateActive(String cellphone);

    Optional<OtpDocument> claimIfMatches(String id, String codeHash, Instant now, int maxAttempts);

    Optional<OtpDocument> findPreviousWithCode(String cellphone, String codeHash, String excludedId);

    Optional<OtpDocument> registerFailedAttempt(String id);
}
