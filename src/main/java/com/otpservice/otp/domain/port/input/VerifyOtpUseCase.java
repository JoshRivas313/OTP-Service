package com.otpservice.otp.domain.port.input;

import com.otpservice.otp.domain.valueobject.Cellphone;
import com.otpservice.otp.domain.valueobject.OtpCode;
import com.otpservice.otp.adapter.in.http.dto.response.OtpVerifyResponse;

public interface VerifyOtpUseCase {

  OtpVerifyResponse verify(VerifyOtpCommand command);

  record VerifyOtpCommand(Cellphone cellphone, OtpCode code) {}
}
