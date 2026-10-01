package com.otpservice.otp.application.port.out;

import com.otpservice.otp.domain.valueobject.EmailAddress;

public interface EmailSender {
    void send(EmailAddress destination, String message);
}
