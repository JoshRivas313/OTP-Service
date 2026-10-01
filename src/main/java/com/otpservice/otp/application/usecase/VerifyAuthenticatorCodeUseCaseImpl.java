package com.otpservice.otp.application.usecase;

import com.otpservice.otp.domain.valueobject.CredentialMode;
import com.otpservice.otp.application.dto.AuthenticatorVerifyResult;
import com.otpservice.otp.application.exception.EnrollmentNotFoundException;
import com.otpservice.otp.application.port.in.AuthenticatorCodeCommand;
import com.otpservice.otp.application.port.in.VerifyAuthenticatorCodeUseCase;
import com.otpservice.otp.application.port.out.CredentialPersistencePort;
import com.otpservice.otp.domain.exception.EnrollmentNotConfirmedException;
import com.otpservice.otp.domain.model.HmacCredential;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyAuthenticatorCodeUseCaseImpl implements VerifyAuthenticatorCodeUseCase {

  private final CredentialPersistencePort persistencePort;
  private final AuthenticatorCodeChecker checker;

  @Override
  public AuthenticatorVerifyResult verify(AuthenticatorCodeCommand command) {
    HmacCredential enrollment = persistencePort.find(command.destination().getValue(), command.type(), CredentialMode.APP)
      .orElseThrow(EnrollmentNotFoundException::new);
    if (!enrollment.isActive()) {
      throw new EnrollmentNotConfirmedException();
    }

    long matched = checker.check(enrollment, command.code(), false);

    log.info("Código de app autenticadora verificado destino={} tipo={}", command.destination().masked(), command.type());
    return AuthenticatorVerifyResult.of("Código verificado correctamente", command.type(), matched);
  }
}
