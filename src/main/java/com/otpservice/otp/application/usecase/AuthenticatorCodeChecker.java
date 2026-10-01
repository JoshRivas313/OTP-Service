package com.otpservice.otp.application.usecase;

import com.otpservice.otp.application.config.AuthenticatorSettings;
import com.otpservice.otp.application.port.out.CredentialPersistencePort;
import com.otpservice.otp.application.port.out.SecretCipherPort;
import com.otpservice.otp.domain.exception.OtpAlreadyUsedException;
import com.otpservice.otp.domain.exception.AuthenticatorLockedException;
import com.otpservice.otp.domain.exception.InvalidOtpException;
import com.otpservice.otp.domain.exception.OtpDomainException;
import com.otpservice.otp.domain.model.HmacCredential;
import com.otpservice.otp.domain.service.CodeMatch;
import com.otpservice.otp.domain.service.HotpVerifier;
import com.otpservice.otp.domain.service.TotpVerifier;
import com.otpservice.otp.domain.valueobject.HmacType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
class AuthenticatorCodeChecker {

  private final CredentialPersistencePort persistencePort;
  private final SecretCipherPort cipher;
  private final AuthenticatorSettings settings;
  private final Clock clock;

  long check(HmacCredential enrollment, String code, boolean activate) {
    Instant now = clock.instant();
    if (enrollment.isLocked(now)) {
      throw new AuthenticatorLockedException(enrollment.minutesLocked(now));
    }

    byte[] secret = cipher.decrypt(enrollment.getSecret(), enrollment.secretContext()).bytes();
    CodeMatch match = switch (enrollment.getType()) {
      case TOTP -> TotpVerifier.verify(secret, code, now, enrollment.getPeriodSeconds(), enrollment.getDigits(),
        settings.totpToleranceSteps(), enrollment.getLastUsedTimeStep());
      case HOTP -> HotpVerifier.verify(secret, code, enrollment.getCounter(), settings.hotpLookAhead(),
        settings.hotpLookAhead(), enrollment.getDigits());
    };

    return switch (match.outcome()) {
      case REUSED -> throw new OtpAlreadyUsedException();
      case NO_MATCH, EXPIRED -> throw registerFailure(enrollment, now);
      case MATCH -> claim(enrollment, match.value(), activate ? now : null);
    };
  }

  private long claim(HmacCredential enrollment, long matched, Instant activatedAt) {
    boolean claimed = enrollment.getType() == HmacType.TOTP
      ? persistencePort.claimTimeStep(enrollment.getId(), matched, activatedAt)
      : persistencePort.claimCounter(enrollment.getId(), enrollment.getCounter(), matched + 1, activatedAt);
    if (!claimed) {
      throw new OtpAlreadyUsedException();
    }
    return matched;
  }

  private OtpDomainException registerFailure(HmacCredential enrollment, Instant now) {
    int attempts = persistencePort.registerFailure(enrollment.getId());
    int max = settings.maxFailedAttempts();
    if (attempts >= max) {
      persistencePort.lock(enrollment.getId(), now.plusSeconds(settings.lockSeconds()));
      log.warn("App autenticadora bloqueada destino={} tipo={}", mask(enrollment.getDestination()), enrollment.getType());
      return new AuthenticatorLockedException(Math.ceilDiv(settings.lockSeconds(), 60));
    }
    return new InvalidOtpException("El código no coincide con el de tu app (intento %d de %d)".formatted(attempts, max));
  }

  private static String mask(String destination) {
    int at = destination.indexOf('@');
    return at > 0 ? destination.charAt(0) + "***" + destination.substring(at) : "***";
  }
}
