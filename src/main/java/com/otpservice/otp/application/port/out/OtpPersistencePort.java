package com.otpservice.otp.application.port.out;

import com.otpservice.otp.domain.model.Otp;
import com.otpservice.otp.domain.valueobject.Purpose;
import java.time.Instant;
import java.util.Optional;

// claimIfMatches y registerFailedAttempt son atomicos a proposito: evitan condiciones de carrera al verificar.
public interface OtpPersistencePort {

  long invalidateActive(String destination, Purpose purpose);

  void save(Otp otp);

  Optional<Otp> findLatest(String destination, Purpose purpose);

  Optional<Otp> claimIfMatches(String id, String codeHash, Instant now, int maxAttempts);

  Optional<Otp> registerFailedAttempt(String id);
}
