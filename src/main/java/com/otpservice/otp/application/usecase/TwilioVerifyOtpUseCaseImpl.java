package com.otpservice.otp.application.usecase;

import com.otpservice.otp.domain.port.input.TwilioVerifyOtpUseCase;
import com.otpservice.otp.domain.port.input.VerifyOtpUseCase;
import com.otpservice.otp.domain.valueobject.Cellphone;
import com.otpservice.otp.domain.valueobject.OtpCode;
import com.otpservice.otp.dto.response.OtpVerifyResponse;
import com.otpservice.otp.exception.ErrorCode;
import com.otpservice.otp.exception.OtpException;
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
      throw new OtpException(ErrorCode.OTP_INVALID);
    }
  }
}
