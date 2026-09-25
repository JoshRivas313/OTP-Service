package com.otpservice.otp.domain.port.output;

public interface CodeHasherPort {
  String hash(String plainCode);
}
