package com.otpservice.otp.domain.port.output;

import com.otpservice.otp.domain.model.Otp;
import java.time.Instant;
import java.util.Optional;

/**
 * Output port for OTP persistence.
 * Mirrors the atomic MongoDB operations needed to keep verification
 * concurrency-safe (claim-if-matches, register-failed-attempt).
 */
public interface OtpPersistencePort {

  long invalidateActive(String cellphone);

  void save(Otp otp);

  Optional<Otp> findLatestByCellphone(String cellphone);

  Optional<Otp> claimIfMatches(String id, String codeHash, Instant now, int maxAttempts);

  Optional<Otp> registerFailedAttempt(String id);
}
