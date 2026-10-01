package com.otpservice.otp.application.port.in;

import com.otpservice.otp.application.dto.AuthenticatorVerifyResult;

public interface ConfirmEnrollmentUseCase {

  AuthenticatorVerifyResult confirm(AuthenticatorCodeCommand command);
}
