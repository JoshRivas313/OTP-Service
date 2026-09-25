package com.otpservice.otp.domain.port.input;

public interface TwilioVerifyOtpUseCase {
  TwilioVerifyOtpResult verify(TwilioVerifyOtpCommand command);

  record TwilioVerifyOtpCommand(
    String cellphone,
    String code,
    String accountSid,
    String authToken,
    String verifyServiceSid
  ) {}

  record TwilioVerifyOtpResult(String message, String code) {}
}
