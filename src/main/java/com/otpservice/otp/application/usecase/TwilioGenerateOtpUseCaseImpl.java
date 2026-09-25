package com.otpservice.otp.application.usecase;

import com.otpservice.otp.domain.model.OtpAggregate;
import com.otpservice.otp.domain.port.input.TwilioGenerateOtpUseCase;
import com.otpservice.otp.domain.port.output.OtpPersistencePort;
import com.otpservice.otp.domain.service.OtpDomainService;
import com.otpservice.otp.security.CodeHasher;
import com.otpservice.otp.sms.twilioconnect.TwilioSessionSmsSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TwilioGenerateOtpUseCaseImpl implements TwilioGenerateOtpUseCase {
  private final OtpDomainService domainService;
  private final OtpPersistencePort persistencePort;
  private final TwilioSessionSmsSender smsPort;
  private final CodeHasher codeHasher;
  private final OtpCodeGenerator codeGenerator;

  @Override
  public TwilioGenerateOtpResult generate(TwilioGenerateOtpCommand command) {
    String plainCode = codeGenerator.generate(command.digits());
    String hashedCode = codeHasher.hash(plainCode);

    OtpAggregate otp = domainService.generateOtp(
      command.cellphone(),
      command.digits(),
      command.durationSeconds(),
      hashedCode
    );

    persistencePort.save(otp);

    smsPort.sendOtpCode(
      command.cellphone(),
      plainCode,
      command.accountSid(),
      command.authToken(),
      command.phoneNumber()
    );

    return new TwilioGenerateOtpResult("SMS enviado", "SMS_SENT");
  }
}
