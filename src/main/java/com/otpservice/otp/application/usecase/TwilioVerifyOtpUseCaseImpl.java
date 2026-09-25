package com.otpservice.otp.application.usecase;

import com.otpservice.otp.domain.exception.InvalidOtpException;
import com.otpservice.otp.domain.port.input.TwilioVerifyOtpUseCase;
import com.otpservice.otp.domain.port.input.VerifyOtpUseCase;
import com.otpservice.otp.domain.valueobject.Cellphone;
import com.otpservice.otp.domain.valueobject.OtpCode;
import com.otpservice.otp.adapter.in.http.dto.response.OtpVerifyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TwilioVerifyOtpUseCaseImpl implements TwilioVerifyOtpUseCase {

  private final VerifyOtpUseCase verifyOtpUseCase;

  @Override
  public OtpVerifyResponse verify(Cellphone cellphone, String code) {
    try {
      return verifyOtpUseCase.verify(new VerifyOtpUseCase.VerifyOtpCommand(cellphone, new OtpCode(code)));
    } catch (IllegalArgumentException exception) {
      throw new InvalidOtpException();
    }
  }
}
