package com.otpservice.otp.application.protocol;

import com.otpservice.otp.domain.valueobject.Destination;
import com.otpservice.otp.domain.valueobject.OtpProtocol;
import com.otpservice.otp.domain.valueobject.Purpose;

// Una forma de emitir y verificar codigos. Los casos de uso eligen una por OtpProtocol y no preguntan cual es.
// El proposito es parte de la identidad: un codigo emitido para un proposito no se verifica con otro.
public interface CodeProtocol {

    OtpProtocol protocol();

    // false cuando el codigo no vence por tiempo (HOTP).
    boolean expires();

    IssuedCode issue(Destination destination, Purpose purpose, int digits, int durationSeconds);

    VerifiedCode verify(Destination destination, Purpose purpose, String code);
}
