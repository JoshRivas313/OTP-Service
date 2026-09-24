package com.otpservice.otp.sms;

import com.otpservice.otp.dto.valueobject.Cellphone;

public interface SmsSender {
    void send(Cellphone destination, String message);
}
