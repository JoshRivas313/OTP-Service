package com.otpservice.otp.application.port.in;

import com.otpservice.otp.application.dto.GenerateOtpResult;
import com.otpservice.otp.application.port.out.SmsSender;
import com.otpservice.otp.domain.valueobject.Cellphone;

public interface GenerateOtpUseCase {

  GenerateOtpResult generate(GenerateOtpCommand command);

  GenerateOtpResult generate(GenerateOtpCommand command, SmsSender sender);

  record GenerateOtpCommand(Cellphone cellphone, Integer digits, Integer durationSeconds) {}
}
