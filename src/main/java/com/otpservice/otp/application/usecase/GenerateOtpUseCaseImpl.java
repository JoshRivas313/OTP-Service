package com.otpservice.otp.application.usecase;

import com.otpservice.otp.domain.model.OtpAggregate;
import com.otpservice.otp.domain.port.input.GenerateOtpUseCase;
import com.otpservice.otp.domain.port.output.OtpPersistencePort;
import com.otpservice.otp.domain.port.output.SmsPort;
import com.otpservice.otp.domain.service.OtpDomainService;
import com.otpservice.otp.domain.valueobject.OtpCode;
import com.otpservice.otp.security.CodeHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GenerateOtpUseCaseImpl implements GenerateOtpUseCase {
  private final OtpDomainService domainService;
  private final OtpPersistencePort persistencePort;
  private final SmsPort smsPort;
  private final CodeHasher codeHasher;
  private final OtpCodeGenerator codeGenerator;

  @Override
  public GenerateOtpCommand generate(GenerateOtpCommand command) {
    String plainCode = codeGenerator.generate(command.digits());
    String hashedCode = codeHasher.hash(plainCode);

    OtpAggregate otp = domainService.generateOtp(
      command.cellphone(),
      command.digits(),
      command.durationSeconds(),
      hashedCode
    );

    persistencePort.save(otp);
    smsPort.sendOtpCode(command.cellphone(), plainCode);

    return command;
  }
}
