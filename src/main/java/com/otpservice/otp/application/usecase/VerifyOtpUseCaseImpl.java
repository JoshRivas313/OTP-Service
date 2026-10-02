package com.otpservice.otp.application.usecase;

import com.otpservice.otp.application.dto.VerifyOtpResult;
import com.otpservice.otp.application.port.in.VerifyOtpUseCase;
import com.otpservice.otp.application.protocol.CodeProtocols;
import com.otpservice.otp.application.protocol.VerifiedCode;
import com.otpservice.otp.domain.valueobject.Destination;
import com.otpservice.otp.domain.valueobject.OtpProtocol;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyOtpUseCaseImpl implements VerifyOtpUseCase {

  private final CodeProtocols protocols;

  @Override
  public VerifyOtpResult verify(VerifyOtpCommand command) {
    Destination destination = command.destination();
    OtpProtocol protocol = command.protocol();

    VerifiedCode verified = protocols.get(protocol).verify(destination, command.purpose(), command.code().getValue());

    log.info("{} verificado destino={} proposito={}", protocol, destination.masked(), command.purpose());
    return VerifyOtpResult.verified(protocol, verified.counter(), verified.timeStep());
  }
}
