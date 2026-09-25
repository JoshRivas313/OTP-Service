package com.otpservice.otp.domain.port.output;

import com.otpservice.otp.domain.model.OtpAggregate;
import java.util.Optional;

public interface OtpPersistencePort {
  void save(OtpAggregate otp);
  Optional<OtpAggregate> findByCellphone(String cellphone);
  void deleteOtpsByExpiration();
}
