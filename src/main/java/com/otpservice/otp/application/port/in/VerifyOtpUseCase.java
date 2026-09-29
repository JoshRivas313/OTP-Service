package com.otpservice.otp.application.port.in;

import com.otpservice.otp.application.dto.VerifyOtpResult;
import com.otpservice.otp.domain.valueobject.Cellphone;
import com.otpservice.otp.domain.valueobject.OtpCode;

public interface VerifyOtpUseCase {

  VerifyOtpResult verify(VerifyOtpCommand command);

  record VerifyOtpCommand(Cellphone cellphone, OtpCode code) {}
}
