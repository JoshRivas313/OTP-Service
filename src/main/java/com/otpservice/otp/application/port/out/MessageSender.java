package com.otpservice.otp.application.port.out;

import com.otpservice.otp.domain.valueobject.Destination;

@FunctionalInterface
public interface MessageSender {
    void send(Destination destination, String message);
}
