package com.otpservice.otp.application.protocol;

import com.otpservice.otp.domain.valueobject.Destination;
import com.otpservice.otp.domain.valueobject.OtpProtocol;

// Una forma de emitir y verificar codigos. Los casos de uso eligen una por OtpProtocol y no preguntan cual es.
public interface CodeProtocol {

    OtpProtocol protocol();

    // false cuando el codigo no vence por tiempo (HOTP).
    boolean expires();

    IssuedCode issue(Destination destination, int digits, int durationSeconds);

    VerifiedCode verify(Destination destination, String code);
}
