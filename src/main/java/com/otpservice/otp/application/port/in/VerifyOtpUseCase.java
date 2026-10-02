package com.otpservice.otp.application.port.in;

import com.otpservice.otp.application.dto.VerifyOtpResult;
import com.otpservice.otp.domain.valueobject.Destination;
import com.otpservice.otp.domain.valueobject.OtpCode;
import com.otpservice.otp.domain.valueobject.OtpProtocol;
import com.otpservice.otp.domain.valueobject.Purpose;

public interface VerifyOtpUseCase {

  VerifyOtpResult verify(VerifyOtpCommand command);

  record VerifyOtpCommand(Destination destination, OtpProtocol protocol, Purpose purpose, OtpCode code) {

    public VerifyOtpCommand {
      protocol = protocol != null ? protocol : OtpProtocol.OTP;
      purpose = purpose != null ? purpose : Purpose.LOGIN;
    }
  }
}
