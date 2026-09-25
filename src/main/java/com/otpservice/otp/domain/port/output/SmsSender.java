package com.otpservice.otp.domain.port.output;

import com.otpservice.otp.domain.valueobject.Cellphone;

public interface SmsSender {
    void send(Cellphone destination, String message);
}
