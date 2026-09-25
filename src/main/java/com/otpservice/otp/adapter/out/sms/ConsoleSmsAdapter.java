package com.otpservice.otp.adapter.out.sms;

import com.otpservice.otp.domain.port.output.SmsPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "sms.provider", havingValue = "console", matchIfMissing = true)
public class ConsoleSmsAdapter implements SmsPort {

  @Override
  public void sendOtpCode(String phoneNumber, String code) {
    log.info("📱 SMS to {} (DEMO MODE - Console): {}", phoneNumber, code);
  }
}
