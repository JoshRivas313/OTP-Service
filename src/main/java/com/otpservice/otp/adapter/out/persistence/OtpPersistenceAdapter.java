package com.otpservice.otp.adapter.out.persistence;

import com.otpservice.otp.domain.model.OtpAggregate;
import com.otpservice.otp.domain.port.output.OtpPersistencePort;
import com.otpservice.otp.repository.OtpRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class OtpPersistenceAdapter implements OtpPersistencePort {
  private final OtpRepository repository;
  private final OtpMapper mapper;

  @Override
  public void save(OtpAggregate otp) {
    var document = mapper.toPersistence(otp);
    repository.save(document);
  }

  @Override
  public Optional<OtpAggregate> findByCellphone(String cellphone) {
    return repository.findByCellphone(cellphone)
      .map(mapper::toDomain);
  }

  @Override
  public void deleteOtpsByExpiration() {
    repository.deleteExpiredOtps();
  }
}
