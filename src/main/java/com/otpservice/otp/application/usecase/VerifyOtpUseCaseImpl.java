package com.otpservice.otp.application.usecase;

import com.otpservice.otp.adapter.config.OtpProperties;
import com.otpservice.otp.domain.model.OtpDocument;
import com.otpservice.otp.domain.port.input.VerifyOtpUseCase;
import com.otpservice.otp.domain.port.output.OtpPersistencePort;
import com.otpservice.otp.domain.valueobject.Cellphone;
import com.otpservice.otp.adapter.in.http.dto.response.OtpVerifyResponse;
import com.otpservice.otp.shared.exception.ErrorCode;
import com.otpservice.otp.shared.exception.OtpException;
import com.otpservice.otp.domain.port.output.CodeHasherPort;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyOtpUseCaseImpl implements VerifyOtpUseCase {

  private final OtpPersistencePort persistencePort;
  private final CodeHasherPort codeHasher;
  private final OtpProperties properties;
  private final Clock clock;

  @Override
  public OtpVerifyResponse verify(VerifyOtpCommand command) {
    Cellphone cellphone = command.cellphone();
    Instant now = clock.instant();
    int maxAttempts = properties.maxAttempts();

    OtpDocument otp = persistencePort.findLatestByCellphone(cellphone.getValue())
      .orElseThrow(() -> new OtpException(ErrorCode.OTP_NOT_FOUND));

    if (otp.isInvalidated()) {
      throw new OtpException(ErrorCode.OTP_INVALIDATED);
    }
    if (otp.isUsed()) {
      throw new OtpException(ErrorCode.OTP_ALREADY_USED);
    }
    if (otp.isExpired(now)) {
      throw new OtpException(ErrorCode.OTP_EXPIRED);
    }
    if (otp.isBlocked(maxAttempts)) {
      throw new OtpException(ErrorCode.OTP_BLOCKED);
    }

    String codeHash = codeHasher.hash(command.code().getValue());
    if (persistencePort.claimIfMatches(otp.getId(), codeHash, now, maxAttempts).isPresent()) {
      log.info("OTP verificado cellphone={}", cellphone.masked());
      return OtpVerifyResponse.verified();
    }

    OtpDocument updated = persistencePort.registerFailedAttempt(otp.getId())
      .orElseThrow(() -> new OtpException(ErrorCode.OTP_NOT_FOUND));

    if (updated.isBlocked(maxAttempts)) {
      throw new OtpException(ErrorCode.OTP_BLOCKED);
    }
    throw new OtpException(ErrorCode.OTP_INVALID,
      "El código es incorrecto (intento %d de %d)".formatted(updated.getAttempts(), maxAttempts));
  }
}
