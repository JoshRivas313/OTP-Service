package com.otpservice.otp.application.usecase;

import java.util.Random;
import org.springframework.stereotype.Component;

@Component
public class OtpCodeGenerator {
  private static final Random random = new Random();

  public String generate(int digits) {
    if (digits < 1 || digits > 10) {
      throw new IllegalArgumentException("Digits must be between 1 and 10");
    }
    int max = (int) Math.pow(10, digits);
    int code = random.nextInt(max);
    return String.format("%0" + digits + "d", code);
  }
}
