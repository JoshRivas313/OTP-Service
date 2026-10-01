package com.otpservice.otp.application.port.in;

import com.otpservice.otp.application.dto.EnrollmentResult;
import com.otpservice.otp.domain.valueobject.HmacType;
import com.otpservice.otp.domain.valueobject.Destination;

public interface EnrollAuthenticatorUseCase {

  EnrollmentResult enroll(EnrollCommand command);

  record EnrollCommand(Destination destination, HmacType type, Integer digits, Integer periodSeconds) {}
}
