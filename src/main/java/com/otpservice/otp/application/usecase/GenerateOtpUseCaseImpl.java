package com.otpservice.otp.application.usecase;

import com.otpservice.otp.adapter.config.OtpProperties;
import com.otpservice.otp.domain.model.Otp;
import com.otpservice.otp.domain.port.input.GenerateOtpUseCase;
import com.otpservice.otp.domain.port.output.OtpPersistencePort;
import com.otpservice.otp.domain.port.output.SmsSender;
import com.otpservice.otp.domain.valueobject.Cellphone;
import com.otpservice.otp.domain.valueobject.OtpCode;
import com.otpservice.otp.domain.valueobject.ValidityWindow;
import com.otpservice.otp.adapter.in.http.dto.response.OtpGenerateResponse;
import com.otpservice.otp.domain.port.output.CodeHasherPort;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GenerateOtpUseCaseImpl implements GenerateOtpUseCase {

  private final OtpPersistencePort persistencePort;
  private final SmsSender defaultSmsSender;
  private final CodeHasherPort codeHasher;
  private final OtpProperties properties;
  private final Clock clock;

  @Override
  public OtpGenerateResponse generate(GenerateOtpCommand command) {
    return generate(command, defaultSmsSender);
  }

  @Override
  public OtpGenerateResponse generate(GenerateOtpCommand command, SmsSender sender) {
    Cellphone cellphone = command.cellphone();
    int digits = command.digits() != null ? command.digits() : properties.digits();
    int durationSeconds = command.durationSeconds() != null ? command.durationSeconds() : properties.durationSeconds();

    persistencePort.invalidateActive(cellphone.getValue());

    Instant now = clock.instant();
    OtpCode code = OtpCode.generate(digits);
    ValidityWindow window = ValidityWindow.from(now, durationSeconds);
    String codeHash = codeHasher.hash(code.getValue());
    Instant purgeAt = window.getExpiresAt().plusSeconds(properties.retentionSeconds());

    persistencePort.save(Otp.issue(
      new Otp.IssueRequest(cellphone, codeHash, digits, window, purgeAt)));

    sender.send(cellphone, properties.messageTemplate().formatted(code.getValue(), durationSeconds));

    return properties.demoMode()
      ? OtpGenerateResponse.sentInDemoMode(code.getValue())
      : OtpGenerateResponse.sent();
  }
}
