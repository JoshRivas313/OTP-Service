package com.otpservice.otp.application.port.in;

import com.otpservice.otp.domain.valueobject.HmacType;
import com.otpservice.otp.domain.valueobject.Destination;

public interface RemoveEnrollmentUseCase {

  void remove(Destination destination, HmacType type);
}
