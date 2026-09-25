package com.otpservice.otp.adapter.out.persistence;

import com.otpservice.otp.document.OtpDocument;
import com.otpservice.otp.domain.port.output.OtpPersistencePort;
import com.otpservice.otp.repository.OtpRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Adapts the Spring Data MongoDB repository to the domain's persistence port.
 * Keeps Spring Data / MongoDB details out of the domain layer.
 */
@Repository
@RequiredArgsConstructor
public class OtpPersistenceAdapter implements OtpPersistencePort {

  private final OtpRepository repository;

  @Override
  public long invalidateActive(String cellphone) {
    return repository.invalidateActive(cellphone);
  }

  @Override
  public void save(OtpDocument document) {
    repository.save(document);
  }

  @Override
  public Optional<OtpDocument> findLatestByCellphone(String cellphone) {
    return repository.findFirstByCellphoneOrderByValidityWindowGeneratedAtDesc(cellphone);
  }

  @Override
  public Optional<OtpDocument> claimIfMatches(String id, String codeHash, Instant now, int maxAttempts) {
    return repository.claimIfMatches(id, codeHash, now, maxAttempts);
  }

  @Override
  public Optional<OtpDocument> registerFailedAttempt(String id) {
    return repository.registerFailedAttempt(id);
  }
}
