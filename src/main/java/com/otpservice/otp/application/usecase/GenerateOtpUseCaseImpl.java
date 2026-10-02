package com.otpservice.otp.application.usecase;

import com.otpservice.otp.application.config.OtpSettings;
import com.otpservice.otp.application.dto.GenerateOtpResult;
import com.otpservice.otp.application.port.in.GenerateOtpUseCase;
import com.otpservice.otp.application.port.out.MessageSender;
import com.otpservice.otp.application.protocol.CodeProtocol;
import com.otpservice.otp.application.protocol.CodeProtocols;
import com.otpservice.otp.application.protocol.IssuedCode;
import com.otpservice.otp.domain.valueobject.Destination;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GenerateOtpUseCaseImpl implements GenerateOtpUseCase {

  private final CodeProtocols protocols;
  private final OtpSettings settings;

  @Override
  public GenerateOtpResult generate(GenerateOtpCommand command, MessageSender sender) {
    Destination destination = command.destination();
    CodeProtocol protocol = protocols.get(command.protocol());
    int digits = command.digits() != null ? command.digits() : settings.digits();
    int durationSeconds = command.durationSeconds() != null ? command.durationSeconds() : settings.durationSeconds();

    IssuedCode issued = protocol.issue(destination, command.purpose(), digits, durationSeconds);

    sender.send(destination,
      OtpMessage.build(command.customMessage(), protocol.expires(), issued, settings.messageTemplate()));

    return settings.demoMode()
      ? GenerateOtpResult.sentInDemoMode(issued.code(), protocol.protocol(), issued.expiresInSeconds(), issued.counter(), issued.timeStep())
      : GenerateOtpResult.sent(protocol.protocol(), issued.expiresInSeconds(), issued.counter(), issued.timeStep());
  }
}
