package com.otpservice.otp.application.usecase;

import com.otpservice.otp.domain.model.OtpAggregate;
import com.otpservice.otp.domain.port.input.VerifyOtpUseCase;
import com.otpservice.otp.domain.port.input.TwilioVerifyOtpUseCase;
import com.otpservice.otp.domain.port.output.OtpPersistencePort;
import com.otpservice.otp.domain.service.OtpDomainService;
import com.otpservice.otp.exception.ErrorCode;
import com.otpservice.otp.exception.OtpException;
import com.otpservice.otp.security.CodeHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TwilioVerifyOtpUseCaseImpl implements TwilioVerifyOtpUseCase {
  private final OtpDomainService domainService;
  private final OtpPersistencePort persistencePort;
  private final CodeHasher codeHasher;

  @Override
  public TwilioVerifyOtpResult verify(TwilioVerifyOtpCommand command) {
    OtpAggregate otp = persistencePort.findByCellphone(command.cellphone())
      .orElseThrow(() -> new OtpException(ErrorCode.OTP_NOT_FOUND));

    if (otp.isExpired(java.time.Instant.now())) {
      throw new OtpException(ErrorCode.OTP_EXPIRED);
    }

    if (otp.isBlocked()) {
      throw new OtpException(ErrorCode.OTP_BLOCKED);
    }

    String hashedCode = codeHasher.hash(command.code());
    if (!domainService.verifyOtp(otp, command.code(), hashedCode)) {
      throw new OtpException(ErrorCode.OTP_INVALID);
    }

    return new TwilioVerifyOtpResult("Código verificado", "OTP_VERIFIED");
  }
}
