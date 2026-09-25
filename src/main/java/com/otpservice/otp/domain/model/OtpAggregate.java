package com.otpservice.otp.domain.model;

import com.otpservice.otp.domain.valueobject.*;
import java.time.Instant;
import lombok.Value;

@Value
public class OtpAggregate {
  String id;
  String cellphone;
  String hashedCode;
  ValidityWindow validityWindow;
  VerificationStatus verificationStatus;
  Instant createdAt;
  Instant expiresAt;

  public static OtpAggregate create(String cellphone, OtpCode code, ValidityWindow window, String hashedCode) {
    return new OtpAggregate(
      java.util.UUID.randomUUID().toString(),
      cellphone,
      hashedCode,
      window,
      VerificationStatus.initial(),
      Instant.now(),
      Instant.now().plusSeconds(window.getDurationSeconds())
    );
  }

  public boolean isExpired(Instant now) {
    return validityWindow.isExpired(now);
  }

  public boolean isBlocked() {
    return verificationStatus.isBlocked();
  }

  public void recordFailedAttempt() {
    // Logik would be handled by domain service
  }

  public void markAsVerified() {
    // Logic would be handled by domain service
  }
}
