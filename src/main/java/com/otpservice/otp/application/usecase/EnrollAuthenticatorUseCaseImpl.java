package com.otpservice.otp.application.usecase;

import com.otpservice.otp.domain.valueobject.CredentialMode;
import com.otpservice.otp.application.config.AuthenticatorSettings;
import com.otpservice.otp.application.dto.EnrollmentResult;
import com.otpservice.otp.application.port.in.EnrollAuthenticatorUseCase;
import com.otpservice.otp.application.port.out.CredentialPersistencePort;
import com.otpservice.otp.application.port.out.QrCodePort;
import com.otpservice.otp.application.port.out.SecretCipherPort;
import com.otpservice.otp.domain.exception.InvalidCodeRequestException;
import com.otpservice.otp.domain.model.HmacCredential;
import com.otpservice.otp.domain.valueobject.AuthenticatorSecret;
import com.otpservice.otp.domain.valueobject.HmacType;
import com.otpservice.otp.domain.valueobject.Destination;
import com.otpservice.otp.domain.valueobject.OtpAuthUri;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollAuthenticatorUseCaseImpl implements EnrollAuthenticatorUseCase {

  private static final Set<Integer> SUPPORTED_DIGITS = Set.of(6, 8);
  private static final Set<Integer> SUPPORTED_PERIODS = Set.of(30, 60);
  private static final int DEFAULT_DIGITS = 6;
  private static final int DEFAULT_PERIOD_SECONDS = 30;

  private final CredentialPersistencePort persistencePort;
  private final SecretCipherPort cipher;
  private final QrCodePort qrCode;
  private final AuthenticatorSettings settings;
  private final Clock clock;

  @Override
  public EnrollmentResult enroll(EnrollCommand command) {
    Destination destination = command.destination();
    HmacType type = command.type();
    int digits = command.digits() != null ? command.digits() : DEFAULT_DIGITS;
    int period = command.periodSeconds() != null ? command.periodSeconds() : DEFAULT_PERIOD_SECONDS;
    ensureSupported(type, digits, period);

    AuthenticatorSecret secret = AuthenticatorSecret.generate();
    String context = HmacCredential.secretContext(destination.getValue(), type, CredentialMode.APP);
    persistencePort.replace(HmacCredential.pending(
      destination.getValue(), type, cipher.encrypt(secret, context), digits, period, clock.instant()));

    String uri = type == HmacType.TOTP
      ? OtpAuthUri.totp(settings.issuer(), destination.getValue(), secret, digits, period)
      : OtpAuthUri.hotp(settings.issuer(), destination.getValue(), secret, digits, 0);

    log.info("App autenticadora pendiente de confirmar destino={} tipo={}", destination.masked(), type);
    return new EnrollmentResult(type, uri, qrCode.svg(uri), secret.base32(), digits,
      type == HmacType.TOTP ? period : null,
      type == HmacType.HOTP ? 0L : null);
  }

  private static void ensureSupported(HmacType type, int digits, int period) {
    if (!SUPPORTED_DIGITS.contains(digits)) {
      throw new InvalidCodeRequestException("Las apps autenticadoras usan 6 u 8 dígitos");
    }
    if (type == HmacType.TOTP && !SUPPORTED_PERIODS.contains(period)) {
      throw new InvalidCodeRequestException("El periodo de TOTP debe ser de 30 o 60 segundos");
    }
  }
}
