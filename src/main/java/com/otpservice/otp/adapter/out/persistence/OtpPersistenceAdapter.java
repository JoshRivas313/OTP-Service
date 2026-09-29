package com.otpservice.otp.adapter.out.persistence;

import com.otpservice.otp.adapter.out.persistence.mapper.OtpPersistenceMapper;
import com.otpservice.otp.domain.model.Otp;
import com.otpservice.otp.application.port.out.OtpPersistencePort;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * Adapts the Spring Data MongoDB repository to the application's persistence port.
 * Keeps Spring Data / MongoDB details (and the OtpDocument representation)
 * out of the domain layer; OtpPersistenceMapper does the Otp <-> OtpDocument
 * conversion at this boundary.
 */
@Repository
@RequiredArgsConstructor
public class OtpPersistenceAdapter implements OtpPersistencePort {

  private final OtpRepository repository;
  private final OtpPersistenceMapper mapper;

  @Override
  public long invalidateActive(String cellphone) {
    return repository.invalidateActive(cellphone);
  }

  @Override
  public void save(Otp otp) {
    repository.save(mapper.toDocument(otp));
  }

  @Override
  public Optional<Otp> findLatestByCellphone(String cellphone) {
    return repository.findFirstByCellphoneOrderByValidityWindowGeneratedAtDesc(cellphone)
      .map(mapper::toDomain);
  }

  @Override
  public Optional<Otp> claimIfMatches(String id, String codeHash, Instant now, int maxAttempts) {
    return repository.claimIfMatches(id, codeHash, now, maxAttempts)
      .map(mapper::toDomain);
  }

  @Override
  public Optional<Otp> registerFailedAttempt(String id) {
    return repository.registerFailedAttempt(id)
      .map(mapper::toDomain);
  }
}
