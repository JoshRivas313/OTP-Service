package com.otpservice.otp.domain.service;

import com.otpservice.otp.domain.model.OtpAggregate;
import com.otpservice.otp.domain.valueobject.*;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OtpDomainService {
  private final Clock clock;

  public OtpAggregate generateOtp(String cellphone, int digits, int durationSeconds, String hashedCode) {
    ValidityWindow window = ValidityWindow.of(durationSeconds);
    return OtpAggregate.create(cellphone, null, window, hashedCode);
  }

  public boolean verifyOtp(OtpAggregate otp, String providedCode, String hashedProvidedCode) {
    if (isExpired(otp)) {
      return false;
    }
    if (isBlocked(otp)) {
      return false;
    }
    return hashedProvidedCode.equals(otp.getHashedCode());
  }

  private boolean isExpired(OtpAggregate otp) {
    return otp.isExpired(clock.instant());
  }

  private boolean isBlocked(OtpAggregate otp) {
    return otp.isBlocked();
  }
}
