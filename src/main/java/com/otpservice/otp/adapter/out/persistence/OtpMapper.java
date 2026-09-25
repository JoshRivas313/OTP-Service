package com.otpservice.otp.adapter.out.persistence;

import com.otpservice.otp.document.OtpDocument;
import com.otpservice.otp.domain.model.OtpAggregate;
import com.otpservice.otp.domain.valueobject.*;
import org.springframework.stereotype.Component;

@Component
public class OtpMapper {

  public OtpDocument toPersistence(OtpAggregate aggregate) {
    return OtpDocument.builder()
      .id(aggregate.getId())
      .cellphone(aggregate.getCellphone())
      .hashedCode(aggregate.getHashedCode())
      .durationSeconds(aggregate.getValidityWindow().getDurationSeconds())
      .attempts(aggregate.getVerificationStatus().getAttempts())
      .isUsed(aggregate.getVerificationStatus().isUsed())
      .createdAt(aggregate.getCreatedAt())
      .expiresAt(aggregate.getExpiresAt())
      .build();
  }

  public OtpAggregate toDomain(OtpDocument document) {
    return new OtpAggregate(
      document.getId(),
      document.getCellphone(),
      document.getHashedCode(),
      ValidityWindow.of(document.getDurationSeconds()),
      new VerificationStatus(document.getAttempts(), document.isUsed()),
      document.getCreatedAt(),
      document.getExpiresAt()
    );
  }
}
