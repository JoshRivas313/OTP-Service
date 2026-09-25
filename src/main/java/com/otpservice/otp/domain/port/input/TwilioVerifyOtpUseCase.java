package com.otpservice.otp.domain.port.input;

import com.otpservice.otp.domain.valueobject.Cellphone;
import com.otpservice.otp.dto.response.OtpVerifyResponse;

public interface TwilioVerifyOtpUseCase {

  OtpVerifyResponse verify(Cellphone cellphone, String code);
}
