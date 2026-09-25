package com.otpservice.otp.application.usecase;

import com.otpservice.otp.domain.port.input.GenerateOtpUseCase;
import com.otpservice.otp.domain.port.input.TwilioGenerateOtpUseCase;
import com.otpservice.otp.domain.valueobject.Cellphone;
import com.otpservice.otp.domain.valueobject.TwilioCredentials;
import com.otpservice.otp.dto.response.OtpGenerateResponse;
import com.otpservice.otp.sms.twilioconnect.TwilioSessionSmsSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TwilioGenerateOtpUseCaseImpl implements TwilioGenerateOtpUseCase {

  private final GenerateOtpUseCase generateOtpUseCase;
  private final TwilioSessionSmsSender sessionSmsSender;

  @Override
  public OtpGenerateResponse generate(
    TwilioCredentials credentials,
    Cellphone cellphone,
    Integer digits,
    Integer durationSeconds
  ) {
    var command = new GenerateOtpUseCase.GenerateOtpCommand(cellphone, digits, durationSeconds);
    return generateOtpUseCase.generate(
      command,
      (destination, message) -> sessionSmsSender.send(credentials, destination, message)
    );
  }
}
