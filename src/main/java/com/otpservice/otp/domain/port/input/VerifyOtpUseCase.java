package com.otpservice.otp.domain.port.input;

public interface VerifyOtpUseCase {
  VerifyOtpResult verify(VerifyOtpCommand command);

  record VerifyOtpCommand(String cellphone, String code) {}
  record VerifyOtpResult(String message, String code) {}
}
