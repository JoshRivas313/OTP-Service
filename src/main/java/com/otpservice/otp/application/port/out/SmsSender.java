package com.otpservice.otp.application.port.out;

import com.otpservice.otp.domain.valueobject.Cellphone;

public interface SmsSender {
    void send(Cellphone destination, String message);
}
