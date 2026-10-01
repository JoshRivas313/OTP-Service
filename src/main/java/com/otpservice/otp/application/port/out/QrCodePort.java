package com.otpservice.otp.application.port.out;

public interface QrCodePort {

  String svg(String content);
}
