package com.otpservice.otp.domain.port.output;

import com.otpservice.otp.domain.model.OtpDocument;
import java.time.Instant;
import java.util.Optional;

/**
 * Output port for OTP persistence.
 * Mirrors the atomic MongoDB operations needed to keep verification
 * concurrency-safe (claim-if-matches, register-failed-attempt).
 */
public interface OtpPersistencePort {

  long invalidateActive(String cellphone);

  void save(OtpDocument document);

  Optional<OtpDocument> findLatestByCellphone(String cellphone);

  Optional<OtpDocument> claimIfMatches(String id, String codeHash, Instant now, int maxAttempts);

  Optional<OtpDocument> registerFailedAttempt(String id);
}
