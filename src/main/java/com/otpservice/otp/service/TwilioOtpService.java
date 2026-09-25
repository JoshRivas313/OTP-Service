package com.otpservice.otp.service;

import com.otpservice.otp.dto.response.OtpGenerateResponse;
import com.otpservice.otp.dto.response.OtpVerifyResponse;
import com.otpservice.otp.dto.valueobject.Cellphone;
import com.otpservice.otp.dto.valueobject.TwilioCredentials;

// El OTP lo genera y valida nuestro backend (Mongo+HMAC, igual que OtpService):
// Twilio solo transporta el SMS, usando el numero y las credenciales que el
// visitante conecto en su sesion. Por eso digits y durationSeconds si
// funcionan de verdad aca (a diferencia de cuando se usaba Twilio Verify).
public interface TwilioOtpService {

    OtpGenerateResponse generateOtp(TwilioCredentials credentials, Cellphone cellphone, Integer digits, Integer durationSeconds);

    OtpVerifyResponse verifyOtp(Cellphone cellphone, String code);
}
