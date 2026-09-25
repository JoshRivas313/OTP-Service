package com.otpservice.otp.domain.port.input;

public interface GenerateOtpUseCase {
  GenerateOtpCommand generate(GenerateOtpCommand command);

  record GenerateOtpCommand(String cellphone, int digits, int durationSeconds) {}
  record GenerateOtpResult(String message, String code) {}
}
