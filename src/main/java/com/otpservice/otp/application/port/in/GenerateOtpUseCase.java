package com.otpservice.otp.application.port.in;

import com.otpservice.otp.application.dto.GenerateOtpResult;
import com.otpservice.otp.application.port.out.MessageSender;
import com.otpservice.otp.domain.valueobject.Destination;
import com.otpservice.otp.domain.valueobject.OtpProtocol;
import com.otpservice.otp.domain.valueobject.Purpose;

public interface GenerateOtpUseCase {

  GenerateOtpResult generate(GenerateOtpCommand command, MessageSender sender);

  // durationSeconds: vigencia en OTP, ventana en TOTP; HOTP lo ignora.
  record GenerateOtpCommand(Destination destination, OtpProtocol protocol, Purpose purpose, Integer digits,
                            Integer durationSeconds,
                            String customMessage) {

    public GenerateOtpCommand {
      protocol = protocol != null ? protocol : OtpProtocol.OTP;
      purpose = purpose != null ? purpose : Purpose.LOGIN;
    }
  }
}
