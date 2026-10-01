package com.otpservice.otp.application.usecase;

import com.otpservice.otp.adapter.config.OtpProperties;
import com.otpservice.otp.application.config.AuthenticatorSettings;
import com.otpservice.otp.application.exception.OtpNotFoundException;
import com.otpservice.otp.application.port.out.CredentialPersistencePort;
import com.otpservice.otp.application.port.out.SecretCipherPort;
import com.otpservice.otp.domain.exception.InvalidCodeRequestException;
import com.otpservice.otp.domain.exception.InvalidOtpException;
import com.otpservice.otp.domain.exception.OtpAlreadyUsedException;
import com.otpservice.otp.domain.exception.OtpBlockedException;
import com.otpservice.otp.domain.exception.OtpExpiredException;
import com.otpservice.otp.domain.model.HmacCredential;
import com.otpservice.otp.domain.service.CodeMatch;
import com.otpservice.otp.domain.service.HmacOtpAlgorithm;
import com.otpservice.otp.domain.service.HotpVerifier;
import com.otpservice.otp.domain.service.TotpVerifier;
import com.otpservice.otp.domain.valueobject.AuthenticatorSecret;
import com.otpservice.otp.domain.valueobject.CredentialMode;
import com.otpservice.otp.domain.valueobject.Destination;
import com.otpservice.otp.domain.valueobject.HmacType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
class DeliveredHmacCodes {

  private static final int MIN_DIGITS = 6;
  private static final int MAX_DIGITS = 8;
  private static final int MIN_PERIOD_SECONDS = 15;
  private static final int MAX_PERIOD_SECONDS = 300;
  private static final int EXPIRED_LOOK_BACK_STEPS = 20;

  private final CredentialPersistencePort persistencePort;
  private final SecretCipherPort cipher;
  private final AuthenticatorSettings settings;
  private final OtpProperties otpProperties;
  private final Clock clock;

  record IssuedCode(String code, Long expiresInSeconds, Long counter, Long timeStep) {
  }

  record VerifiedCode(Long counter, Long timeStep) {
  }

  IssuedCode issue(Destination destination, HmacType type, int digits, int periodSeconds) {
    ensureSupported(type, digits, periodSeconds);
    Instant now = clock.instant();
    String context = HmacCredential.secretContext(destination.getValue(), type, CredentialMode.DELIVERED);
    HmacCredential credential = persistencePort.createIfAbsent(HmacCredential.delivered(
      destination.getValue(), type, cipher.encrypt(AuthenticatorSecret.generate(), context), digits, periodSeconds, now));
    credential = persistencePort.issue(credential.getId(), digits, type == HmacType.TOTP ? periodSeconds : 0);
    byte[] secret = cipher.decrypt(credential.getSecret(), credential.secretContext()).bytes();

    if (type == HmacType.HOTP) {
      long counter = credential.getIssuedCounter() - 1;
      return new IssuedCode(HmacOtpAlgorithm.hotp(secret, counter, digits), null, counter, null);
    }
    long timeStep = HmacOtpAlgorithm.timeStep(now, periodSeconds);
    Instant expiresAt = Instant.ofEpochSecond((timeStep + 1 + settings.totpToleranceSteps()) * periodSeconds);
    return new IssuedCode(HmacOtpAlgorithm.totp(secret, now, periodSeconds, digits),
      Duration.between(now, expiresAt).toSeconds(), null, timeStep);
  }

  VerifiedCode verify(Destination destination, HmacType type, String code) {
    HmacCredential credential = persistencePort.find(destination.getValue(), type, CredentialMode.DELIVERED)
      .orElseThrow(OtpNotFoundException::new);
    Instant now = clock.instant();
    if (credential.isLocked(now)) {
      throw new OtpBlockedException();
    }

    byte[] secret = cipher.decrypt(credential.getSecret(), credential.secretContext()).bytes();
    CodeMatch match = type == HmacType.TOTP
      ? TotpVerifier.verify(secret, code, now, credential.getPeriodSeconds(), credential.getDigits(),
          settings.totpToleranceSteps(), credential.getLastUsedTimeStep(), EXPIRED_LOOK_BACK_STEPS)
      : HotpVerifier.verify(secret, code, credential.getCounter(), pendingWindow(credential),
          settings.hotpLookAhead(), credential.getDigits());

    return switch (match.outcome()) {
      case REUSED -> throw new OtpAlreadyUsedException();
      case EXPIRED -> throw new OtpExpiredException();
      case NO_MATCH -> throw registerFailure(credential, now);
      case MATCH -> claim(credential, match.value());
    };
  }

  private long pendingWindow(HmacCredential credential) {
    return Math.min(credential.pendingCodes(), settings.hotpLookAhead()) - 1;
  }

  private VerifiedCode claim(HmacCredential credential, long matched) {
    boolean claimed = credential.getType() == HmacType.TOTP
      ? persistencePort.claimTimeStep(credential.getId(), matched, null)
      : persistencePort.claimCounter(credential.getId(), credential.getCounter(), matched + 1, null);
    if (!claimed) {
      throw new OtpAlreadyUsedException();
    }
    return credential.getType() == HmacType.TOTP ? new VerifiedCode(null, matched) : new VerifiedCode(matched, null);
  }

  private RuntimeException registerFailure(HmacCredential credential, Instant now) {
    int attempts = persistencePort.registerFailure(credential.getId());
    int max = otpProperties.maxAttempts();
    if (attempts >= max) {
      persistencePort.lock(credential.getId(), now.plusSeconds(otpProperties.retentionSeconds()));
      return new OtpBlockedException();
    }
    return new InvalidOtpException("El código es incorrecto (intento %d de %d)".formatted(attempts, max));
  }

  private static void ensureSupported(HmacType type, int digits, int periodSeconds) {
    if (digits < MIN_DIGITS || digits > MAX_DIGITS) {
      throw new InvalidCodeRequestException("%s usa entre %d y %d dígitos".formatted(type, MIN_DIGITS, MAX_DIGITS));
    }
    if (type == HmacType.TOTP && (periodSeconds < MIN_PERIOD_SECONDS || periodSeconds > MAX_PERIOD_SECONDS)) {
      throw new InvalidCodeRequestException("La ventana de TOTP debe estar entre %d y %d segundos"
        .formatted(MIN_PERIOD_SECONDS, MAX_PERIOD_SECONDS));
    }
  }
}
