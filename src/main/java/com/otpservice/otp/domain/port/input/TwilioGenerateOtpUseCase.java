package com.otpservice.otp.domain.port.input;

public interface TwilioGenerateOtpUseCase {
  TwilioGenerateOtpResult generate(TwilioGenerateOtpCommand command);

  record TwilioGenerateOtpCommand(
    String cellphone,
    int digits,
    int durationSeconds,
    String accountSid,
    String authToken,
    String verifyServiceSid,
    String phoneNumber
  ) {}

  record TwilioGenerateOtpResult(String message, String code) {}
}
