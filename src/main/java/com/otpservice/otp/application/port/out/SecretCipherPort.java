package com.otpservice.otp.application.port.out;

import com.otpservice.otp.domain.valueobject.HmacSecret;
import com.otpservice.otp.domain.valueobject.EncryptedSecret;

public interface SecretCipherPort {

  EncryptedSecret encrypt(HmacSecret secret, String context);

  HmacSecret decrypt(EncryptedSecret secret, String context);
}
