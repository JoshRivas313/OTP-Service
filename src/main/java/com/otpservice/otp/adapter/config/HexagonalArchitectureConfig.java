package com.otpservice.otp.adapter.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan({
  "com.otpservice.otp.domain",
  "com.otpservice.otp.application",
  "com.otpservice.otp.adapter"
})
public class HexagonalArchitectureConfig {
}
