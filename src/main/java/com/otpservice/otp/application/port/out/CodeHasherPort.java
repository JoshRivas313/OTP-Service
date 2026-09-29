package com.otpservice.otp.application.port.out;

public interface CodeHasherPort {
  String hash(String plainCode);
}
