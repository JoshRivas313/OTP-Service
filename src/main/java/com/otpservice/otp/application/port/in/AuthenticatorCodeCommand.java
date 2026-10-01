package com.otpservice.otp.application.port.in;

import com.otpservice.otp.domain.valueobject.HmacType;
import com.otpservice.otp.domain.valueobject.Destination;

public record AuthenticatorCodeCommand(Destination destination, HmacType type, String code) {}
