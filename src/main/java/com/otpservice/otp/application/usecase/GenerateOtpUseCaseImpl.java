package com.otpservice.otp.application.usecase;

import com.otpservice.otp.adapter.config.OtpProperties;
import com.otpservice.otp.application.dto.GenerateOtpResult;
import com.otpservice.otp.domain.model.Otp;
import com.otpservice.otp.application.port.in.GenerateOtpUseCase;
import com.otpservice.otp.application.port.out.OtpPersistencePort;
import com.otpservice.otp.application.port.out.MessageSender;
import com.otpservice.otp.domain.valueobject.Destination;
import com.otpservice.otp.domain.valueobject.OtpCode;
import com.otpservice.otp.domain.valueobject.OtpProtocol;
import com.otpservice.otp.domain.valueobject.ValidityWindow;
import com.otpservice.otp.application.port.out.CodeHasherPort;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GenerateOtpUseCaseImpl implements GenerateOtpUseCase {

  private static final String HOTP_MESSAGE_TEMPLATE = "Tu código de verificación es %s. Sirve hasta que lo uses.";

  private final OtpPersistencePort persistencePort;
  private final CodeHasherPort codeHasher;
  private final DeliveredHmacCodes hmacCodes;
  private final OtpProperties properties;
  private final Clock clock;

  @Override
  public GenerateOtpResult generate(GenerateOtpCommand command, MessageSender sender) {
    Destination destination = command.destination();
    OtpProtocol protocol = command.protocol();
    int digits = command.digits() != null ? command.digits() : properties.digits();
    int durationSeconds = command.durationSeconds() != null ? command.durationSeconds() : properties.durationSeconds();

    DeliveredHmacCodes.IssuedCode issued = protocol.isHmac()
      ? hmacCodes.issue(destination, protocol.hmacType(), digits, durationSeconds)
      : random(destination, digits, durationSeconds);

    sender.send(destination, buildMessage(command.customMessage(), protocol, issued));

    return properties.demoMode()
      ? GenerateOtpResult.sentInDemoMode(issued.code(), protocol, issued.expiresInSeconds(), issued.counter(), issued.timeStep())
      : GenerateOtpResult.sent(protocol, issued.expiresInSeconds(), issued.counter(), issued.timeStep());
  }

  private DeliveredHmacCodes.IssuedCode random(Destination destination, int digits, int durationSeconds) {
    persistencePort.invalidateActive(destination.getValue());

    Instant now = clock.instant();
    OtpCode code = OtpCode.generate(digits);
    ValidityWindow window = ValidityWindow.from(now, durationSeconds);
    String codeHash = codeHasher.hash(code.getValue());
    Instant purgeAt = window.getExpiresAt().plusSeconds(properties.retentionSeconds());

    persistencePort.save(Otp.issue(
      new Otp.IssueRequest(destination, codeHash, digits, window, purgeAt)));
    return new DeliveredHmacCodes.IssuedCode(code.getValue(), (long) durationSeconds, null, null);
  }

  private String buildMessage(String customMessage, OtpProtocol protocol, DeliveredHmacCodes.IssuedCode issued) {
    boolean noExpiry = protocol == OtpProtocol.HOTP;
    if (customMessage == null || customMessage.isBlank()) {
      return noExpiry
        ? HOTP_MESSAGE_TEMPLATE.formatted(issued.code())
        : properties.messageTemplate().formatted(issued.code(), issued.expiresInSeconds());
    }
    String message = customMessage.replace("{code}", issued.code());
    return noExpiry
      ? message.replace("Vence en {seconds} segundos.", "Sirve hasta que lo uses.").replace("{seconds}", "")
      : message.replace("{seconds}", String.valueOf(issued.expiresInSeconds()));
  }
}
