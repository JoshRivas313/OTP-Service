package com.otpservice.otp.application.usecase;

import com.otpservice.otp.adapter.config.OtpProperties;
import com.otpservice.otp.application.dto.VerifyOtpResult;
import com.otpservice.otp.domain.exception.InvalidOtpException;
import com.otpservice.otp.domain.exception.OtpAlreadyUsedException;
import com.otpservice.otp.domain.exception.OtpBlockedException;
import com.otpservice.otp.domain.exception.OtpExpiredException;
import com.otpservice.otp.domain.exception.OtpInvalidatedException;
import com.otpservice.otp.application.exception.OtpNotFoundException;
import com.otpservice.otp.domain.model.Otp;
import com.otpservice.otp.application.port.in.VerifyOtpUseCase;
import com.otpservice.otp.application.port.out.CodeHasherPort;
import com.otpservice.otp.application.port.out.OtpPersistencePort;
import com.otpservice.otp.domain.valueobject.Destination;
import com.otpservice.otp.domain.valueobject.OtpProtocol;
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
  private final DeliveredHmacCodes hmacCodes;
  private final OtpProperties properties;
  private final Clock clock;

  @Override
  public VerifyOtpResult verify(VerifyOtpCommand command) {
    Destination destination = command.destination();
    OtpProtocol protocol = command.protocol();
    if (protocol.isHmac()) {
      DeliveredHmacCodes.VerifiedCode verified =
        hmacCodes.verify(destination, protocol.hmacType(), command.code().getValue());
      log.info("{} verificado destino={}", protocol, destination.masked());
      return VerifyOtpResult.verified(protocol, verified.counter(), verified.timeStep());
    }
    Instant now = clock.instant();
    int maxAttempts = properties.maxAttempts();

    Otp otp = persistencePort.findLatestByDestination(destination.getValue())
      .orElseThrow(OtpNotFoundException::new);

    if (otp.isInvalidated()) {
      throw new OtpInvalidatedException();
    }
    if (otp.isUsed()) {
      throw new OtpAlreadyUsedException();
    }
    if (otp.isExpired(now)) {
      throw new OtpExpiredException();
    }
    if (otp.isBlocked(maxAttempts)) {
      throw new OtpBlockedException();
    }

    String codeHash = codeHasher.hash(command.code().getValue());
    if (persistencePort.claimIfMatches(otp.getId(), codeHash, now, maxAttempts).isPresent()) {
      log.info("OTP verificado destino={}", destination.masked());
      return VerifyOtpResult.verified();
    }

    Otp updated = persistencePort.registerFailedAttempt(otp.getId())
      .orElseThrow(OtpNotFoundException::new);

    if (updated.isBlocked(maxAttempts)) {
      throw new OtpBlockedException();
    }
    throw new InvalidOtpException(
      "El código es incorrecto (intento %d de %d)".formatted(updated.getAttempts(), maxAttempts));
  }
}
