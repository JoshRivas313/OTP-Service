package com.otpservice.otp.application.port.out;

import com.otpservice.otp.domain.valueobject.AuthenticatorSecret;
import com.otpservice.otp.domain.valueobject.EncryptedSecret;

public interface SecretCipherPort {

  EncryptedSecret encrypt(AuthenticatorSecret secret, String context);

  AuthenticatorSecret decrypt(EncryptedSecret secret, String context);
}
