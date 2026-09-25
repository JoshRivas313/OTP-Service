package com.otpservice.otp.adapter.out.sms;

import com.otpservice.otp.domain.port.output.SmsPort;
import com.otpservice.otp.sms.TwilioSmsSender;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "sms.provider", havingValue = "twilio")
public class TwilioSmsAdapter implements SmsPort {
  private final TwilioSmsSender twilioSmsSender;

  @Override
  public void sendOtpCode(String phoneNumber, String code) {
    twilioSmsSender.send(phoneNumber, "Tu código OTP es: " + code);
  }
}
