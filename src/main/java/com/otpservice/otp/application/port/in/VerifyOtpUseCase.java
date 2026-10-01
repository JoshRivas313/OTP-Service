package com.otpservice.otp.application.port.in;

import com.otpservice.otp.application.dto.VerifyOtpResult;
import com.otpservice.otp.domain.valueobject.Destination;
import com.otpservice.otp.domain.valueobject.OtpCode;
import com.otpservice.otp.domain.valueobject.OtpProtocol;

public interface VerifyOtpUseCase {

  VerifyOtpResult verify(VerifyOtpCommand command);

  record VerifyOtpCommand(Destination destination, OtpProtocol protocol, OtpCode code) {

    public VerifyOtpCommand {
      protocol = protocol != null ? protocol : OtpProtocol.OTP;
    }
  }
}
