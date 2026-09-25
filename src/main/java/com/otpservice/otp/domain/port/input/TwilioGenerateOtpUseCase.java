package com.otpservice.otp.domain.port.input;

import com.otpservice.otp.domain.valueobject.Cellphone;
import com.otpservice.otp.domain.valueobject.TwilioCredentials;
import com.otpservice.otp.adapter.in.http.dto.response.OtpGenerateResponse;

public interface TwilioGenerateOtpUseCase {

  OtpGenerateResponse generate(
    TwilioCredentials credentials,
    Cellphone cellphone,
    Integer digits,
    Integer durationSeconds
  );
}
