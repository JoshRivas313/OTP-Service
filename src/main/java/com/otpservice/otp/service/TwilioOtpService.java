package com.otpservice.otp.service;

import com.otpservice.otp.dto.response.OtpGenerateResponse;
import com.otpservice.otp.dto.response.OtpVerifyResponse;
import com.otpservice.otp.dto.valueobject.Cellphone;
import com.otpservice.otp.dto.valueobject.TwilioCredentials;

// Analogo a OtpService, pero contra Twilio Verify en vez de Mongo: el
// estado del codigo (expiracion, intentos) lo maneja Twilio del otro lado,
// aca no se persiste nada.
public interface TwilioOtpService {

    OtpGenerateResponse generateOtp(TwilioCredentials credentials, Cellphone cellphone);

    OtpVerifyResponse verifyOtp(TwilioCredentials credentials, Cellphone cellphone, String code);
}
