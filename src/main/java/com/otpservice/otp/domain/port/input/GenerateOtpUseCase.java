package com.otpservice.otp.domain.port.input;

import com.otpservice.otp.domain.port.output.SmsSender;
import com.otpservice.otp.domain.valueobject.Cellphone;
import com.otpservice.otp.dto.response.OtpGenerateResponse;

public interface GenerateOtpUseCase {

  OtpGenerateResponse generate(GenerateOtpCommand command);

  OtpGenerateResponse generate(GenerateOtpCommand command, SmsSender sender);

  record GenerateOtpCommand(Cellphone cellphone, Integer digits, Integer durationSeconds) {}
}
