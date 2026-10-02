package com.otpservice.otp.adapter.out.persistence;

import com.otpservice.otp.adapter.out.persistence.mapper.OtpPersistenceMapper;
import com.otpservice.otp.domain.model.Otp;
import com.otpservice.otp.domain.valueobject.Purpose;
import com.otpservice.otp.application.port.out.OtpPersistencePort;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;

@Repository
@Profile("mongo")
@RequiredArgsConstructor
public class OtpPersistenceAdapter implements OtpPersistencePort {

  private final OtpRepository repository;
  private final OtpPersistenceMapper mapper;

  @Override
  public long invalidateActive(String destination, Purpose purpose) {
    return repository.invalidateActive(destination, purpose);
  }

  @Override
  public void save(Otp otp) {
    repository.save(mapper.toDocument(otp));
  }

  @Override
  public Optional<Otp> findLatest(String destination, Purpose purpose) {
    return repository.findFirstByDestinationAndPurposeOrderByValidityWindowGeneratedAtDesc(destination, purpose)
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
