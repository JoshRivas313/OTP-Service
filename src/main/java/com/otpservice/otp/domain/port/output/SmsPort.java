package com.otpservice.otp.domain.port.output;

public interface SmsPort {
  void sendOtpCode(String phoneNumber, String code);
}
