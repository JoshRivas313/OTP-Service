package com.otpservice.otp.application.port.out;

import com.otpservice.otp.domain.model.Otp;
import java.time.Instant;
import java.util.Optional;

// claimIfMatches y registerFailedAttempt son atomicos a proposito: evitan condiciones de carrera al verificar.
public interface OtpPersistencePort {

  long invalidateActive(String destination);

  void save(Otp otp);

  Optional<Otp> findLatestByDestination(String destination);

  Optional<Otp> claimIfMatches(String id, String codeHash, Instant now, int maxAttempts);

  Optional<Otp> registerFailedAttempt(String id);
}
