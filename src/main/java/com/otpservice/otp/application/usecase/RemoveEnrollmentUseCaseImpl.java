package com.otpservice.otp.application.usecase;

import com.otpservice.otp.domain.valueobject.CredentialMode;
import com.otpservice.otp.application.exception.EnrollmentNotFoundException;
import com.otpservice.otp.application.port.in.RemoveEnrollmentUseCase;
import com.otpservice.otp.application.port.out.CredentialPersistencePort;
import com.otpservice.otp.domain.valueobject.HmacType;
import com.otpservice.otp.domain.valueobject.Destination;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RemoveEnrollmentUseCaseImpl implements RemoveEnrollmentUseCase {

  private final CredentialPersistencePort persistencePort;

  @Override
  public void remove(Destination destination, HmacType type) {
    if (!persistencePort.delete(destination.getValue(), type, CredentialMode.APP)) {
      throw new EnrollmentNotFoundException();
    }
    log.info("App autenticadora eliminada destino={} tipo={}", destination.masked(), type);
  }
}
